package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.CodeInsightColors;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.xml.util.XmlUtil;

import java.awt.*;
import java.util.List;

public class HighlightInfo {
  public static final HighlightInfo[] EMPTY_ARRAY = new HighlightInfo[0];
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.HighlightInfo");
  private Boolean myNeedsUpdateOnTyping = null;

  public HighlightSeverity getSeverity() {
    return severity;
  }

  private TextAttributes forcedTextAttributes;

  public TextAttributes getTextAttributes() {
    return forcedTextAttributes == null ? getAttributesByType(type) : forcedTextAttributes;
  }
  static TextAttributes getAttributesByType(HighlightInfoType type) {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    TextAttributesKey key = type.getAttributesKey();
    return scheme.getAttributes(key);
  }

  public Color getErrorStripeMarkColor() {
    if (forcedTextAttributes != null) {
      return forcedTextAttributes.getErrorStripeColor();
    }
    HighlightSeverity severity = getSeverity();
    if (severity == HighlightSeverity.ERROR) {
      return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES).getErrorStripeColor();
    }
    if (severity == HighlightSeverity.WARNING) {
      return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.WARNINGS_ATTRIBUTES).getErrorStripeColor();
    }
    return getAttributesByType(type).getErrorStripeColor();
  }

  public static HighlightInfo createHighlightInfo(HighlightInfoType type, PsiElement element, String description) {
    return createHighlightInfo(type, element, description, xmlEscapeToolTip(description));
  }

  private static String xmlEscapeToolTip(String description) {
    return "<html><body>"+XmlUtil.escapeString(description)+"</body></html>";
  }

  public static HighlightInfo createHighlightInfo(HighlightInfoType type, PsiElement element, String description, String toolTip) {
    TextRange range = element.getTextRange();
    int start = range.getStartOffset();
    int end = range.getEndOffset();
    return createHighlightInfo(type, start, end, description, toolTip);
  }

  public static HighlightInfo createHighlightInfo(HighlightInfoType type, int start, int end, String description, String toolTip) {
    Object[] filters = ApplicationManager.getApplication().getComponents(HighlightInfoFilter.class);
    for (int i = 0; i < filters.length; i++) {
      HighlightInfoFilter filter = (HighlightInfoFilter)filters[i];
      if (!filter.accept(type, null)) {
        return null;
      }
    }

    return new HighlightInfo(type, start, end, description, toolTip);
  }

  public static HighlightInfo createHighlightInfo(HighlightInfoType type, int start, int end, String description) {
    return createHighlightInfo(type, start, end, description, xmlEscapeToolTip(description));
  }

  public static HighlightInfo createHighlightInfo(HighlightInfoType type, TextRange textRange, String description) {
    return createHighlightInfo(type, textRange.getStartOffset(), textRange.getEndOffset(), description);
  }
  public static HighlightInfo createHighlightInfo(HighlightInfoType type, TextRange textRange, String description, String toolTip) {
    return createHighlightInfo(type, textRange.getStartOffset(), textRange.getEndOffset(), description, toolTip);
  }
  public static HighlightInfo createHighlightInfo(HighlightInfoType type, TextRange textRange, String description, TextAttributes textAttributes) {
    // do not use HighlightInfoFilter
    HighlightInfo highlightInfo = new HighlightInfo(type, textRange.getStartOffset(), textRange.getEndOffset(), description, xmlEscapeToolTip(description));
    highlightInfo.forcedTextAttributes = textAttributes;
    return highlightInfo;
  }

  public boolean needUpdateOnTyping() {
    if (myNeedsUpdateOnTyping != null) return myNeedsUpdateOnTyping.booleanValue();
    
    if (type == HighlightInfoType.TODO) return false;
    if (type == HighlightInfoType.LOCAL_VAR) return false;
    if (type == HighlightInfoType.INSTANCE_FIELD) return false;
    if (type == HighlightInfoType.STATIC_FIELD) return false;
    if (type == HighlightInfoType.PARAMETER) return false;
    if (type == HighlightInfoType.METHOD_CALL) return false;
    if (type == HighlightInfoType.METHOD_DECLARATION) return false;
    if (type == HighlightInfoType.STATIC_METHOD) return false;
    if (type == HighlightInfoType.CONSTRUCTOR_CALL) return false;
    if (type == HighlightInfoType.CONSTRUCTOR_DECLARATION) return false;
    if (type == HighlightInfoType.INTERFACE_NAME) return false;
    if (type == HighlightInfoType.CLASS_NAME) return false;
    return true;
  }

  public HighlightInfoType type;
  public int group;
  public final int startOffset;
  public final int endOffset;

  public int fixStartOffset;
  public int fixEndOffset;
  public RangeMarker fixMarker;

  public String description;
  public String toolTip;
  public final HighlightSeverity severity;

  public boolean isAfterEndOfLine = false;
  public int navigationShift = 0;

  public RangeHighlighter highlighter;
  public String text;

  public List<Pair<IntentionAction, TextRange>> quickFixActionRanges;
  public List<Pair<IntentionAction, RangeMarker>> quickFixActionMarkers;

  public HighlightInfo(HighlightInfoType type, int startOffset, int endOffset, String description, String toolTip) {
    this.type = type;
    this.startOffset = startOffset;
    this.endOffset = endOffset;
    this.fixStartOffset = startOffset;
    this.fixEndOffset = endOffset;
    this.description = description;
    this.severity = type.getSeverity();
    this.toolTip = toolTip;
    LOG.assertTrue(startOffset >= 0);
    LOG.assertTrue(startOffset <= endOffset);
  }

  public HighlightInfo(final TextAttributesKey textAttributesKey,
                       final HighlightInfoType type,
                       final int startOffset,
                       final int endOffset,
                       final String description,
                       final String toolTip,
                       final HighlightSeverity severity,
                       final boolean afterEndOfLine,
                       final boolean needsUpdateOnTyping) {
    this.forcedTextAttributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(textAttributesKey);
    this.type = type;
    this.startOffset = startOffset;
    this.endOffset = endOffset;
    this.description = description;
    this.toolTip = toolTip;
    this.severity = severity;
    isAfterEndOfLine = afterEndOfLine;
    myNeedsUpdateOnTyping = Boolean.valueOf(needsUpdateOnTyping);
  }

  public boolean equals(Object obj) {
    return obj == this ||
      (obj instanceof HighlightInfo &&
      ((HighlightInfo)obj).getSeverity() == getSeverity() &&
      ((HighlightInfo)obj).startOffset == startOffset &&
      ((HighlightInfo)obj).endOffset == endOffset &&
      ((HighlightInfo)obj).type == type &&
      //Do not include fix offsets!!!
      Comparing.strEqual(((HighlightInfo)obj).description, description)
      );
  }

  public int hashCode() {
    return startOffset;
  }

  public String toString() {
    return "HighlightInfo(" +
           "text='" + text + "'" +
           ", description='" + description + "'" +
           ", toolTip='" + toolTip + "'" +
           ")";
  }

  public static HighlightInfo createHighlightInfo(HighlightInfoType type, ASTNode childByRole, String localizedMessage) {
    return createHighlightInfo(type, SourceTreeToPsiMap.treeElementToPsi(childByRole), localizedMessage);
  }

}