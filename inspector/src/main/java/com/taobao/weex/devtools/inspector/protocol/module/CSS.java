/*
 * Copyright (c) 2014-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.taobao.weex.devtools.inspector.protocol.module;

import com.taobao.weex.devtools.common.ListUtil;
import com.taobao.weex.devtools.common.LogUtil;
import com.taobao.weex.devtools.common.Util;
import com.taobao.weex.devtools.inspector.elements.Document;
import com.taobao.weex.devtools.inspector.elements.Origin;
import com.taobao.weex.devtools.inspector.elements.StyleAccumulator;
import com.taobao.weex.devtools.inspector.elements.W3CStyleConstants;
import com.taobao.weex.devtools.inspector.helper.ChromePeerManager;
import com.taobao.weex.devtools.inspector.helper.PeersRegisteredListener;
import com.taobao.weex.devtools.inspector.jsonrpc.JsonRpcPeer;
import com.taobao.weex.devtools.inspector.jsonrpc.JsonRpcResult;
import com.taobao.weex.devtools.inspector.protocol.ChromeDevtoolsDomain;
import com.taobao.weex.devtools.inspector.protocol.ChromeDevtoolsMethod;
import com.taobao.weex.devtools.json.ObjectMapper;
import com.taobao.weex.devtools.json.annotation.JsonProperty;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CSS implements ChromeDevtoolsDomain {
  private final ChromePeerManager mPeerManager;
  private final Document mDocument;
  private final ObjectMapper mObjectMapper;

  public CSS(Document document) {
    mDocument = Util.throwIfNull(document);
    mObjectMapper = new ObjectMapper();
    mPeerManager = new ChromePeerManager();
    mPeerManager.setListener(new PeerManagerListener());
  }

  @ChromeDevtoolsMethod
  public void enable(JsonRpcPeer peer, JSONObject params) {
  }

  @ChromeDevtoolsMethod
  public void disable(JsonRpcPeer peer, JSONObject params) {
  }

  @ChromeDevtoolsMethod
  public JsonRpcResult getComputedStyleForNode(JsonRpcPeer peer, JSONObject params) {
    final GetComputedStyleForNodeRequest request = mObjectMapper.convertValue(
        params,
        GetComputedStyleForNodeRequest.class);

    final GetComputedStyleForNodeResult result = new GetComputedStyleForNodeResult();
    result.computedStyle = new ArrayList<>();

    mDocument.postAndWait(new Runnable() {
      @Override
      public void run() {
        Object element = mDocument.getElementForNodeId(request.nodeId);

        if (element == null) {
          LogUtil.e("Tried to get the style of an element that does not exist, using nodeid=" +
              request.nodeId);

          return;
        }

        // fix devtools not show the box
        mockStyleProperty(result.computedStyle, sProperties);
        mDocument.getElementStyles(
            element,
            new StyleAccumulator() {
              @Override
              public void store(String name, String value, boolean isDefault) {
                if (!isDefault) {
                  CSSComputedStyleProperty property = new CSSComputedStyleProperty();
                  if (!name.startsWith("v-")) {
                    property.name = name;
                    property.value = value;
                    result.computedStyle.add(property);
                  }
                }
              }
            });
      }
    });

    return result;
  }

  private static final HashMap<String, String> sProperties = new HashMap<String, String>();

  static {
    sProperties.put(W3CStyleConstants.WIDTH, "");
    sProperties.put(W3CStyleConstants.HEIGHT, "");

    sProperties.put(W3CStyleConstants.PADDING_LEFT, "");
    sProperties.put(W3CStyleConstants.PADDING_TOP, "");
    sProperties.put(W3CStyleConstants.PADDING_RIGHT, "");
    sProperties.put(W3CStyleConstants.PADDING_BOTTOM, "");

    sProperties.put(W3CStyleConstants.BORDER_LEFT_WIDTH, "");
    sProperties.put(W3CStyleConstants.BORDER_TOP_WIDTH, "");
    sProperties.put(W3CStyleConstants.BORDER_RIGHT_WIDTH, "");
    sProperties.put(W3CStyleConstants.BORDER_BOTTOM_WIDTH, "");

    sProperties.put(W3CStyleConstants.MARGIN_LEFT, "");
    sProperties.put(W3CStyleConstants.MARGIN_TOP, "");
    sProperties.put(W3CStyleConstants.MARGIN_RIGHT, "");
    sProperties.put(W3CStyleConstants.MARGIN_BOTTOM, "");

    sProperties.put(W3CStyleConstants.LEFT, "");
    sProperties.put(W3CStyleConstants.TOP, "");
    sProperties.put(W3CStyleConstants.RIGHT, "");
    sProperties.put(W3CStyleConstants.BOTTOM, "");
  }

  private void mockStyleProperty(List<CSSComputedStyleProperty> computedStyle, HashMap<String, String> properties) {
    for (Map.Entry<String, String> entry : properties.entrySet()) {
      addStyleProperty(computedStyle, entry.getKey(), entry.getValue());
    }
  }

  private void addStyleProperty(List<CSSComputedStyleProperty> computedStyle, String name, String value) {
    CSSComputedStyleProperty property = new CSSComputedStyleProperty();
    property.name = name;
    property.value = value;
    computedStyle.add(property);
  }

  @ChromeDevtoolsMethod
  public JsonRpcResult getMatchedStylesForNode(JsonRpcPeer peer, JSONObject params) {
    final GetMatchedStylesForNodeRequest request = mObjectMapper.convertValue(
        params,
        GetMatchedStylesForNodeRequest.class);

    final GetMatchedStylesForNodeResult result = new GetMatchedStylesForNodeResult();
    List<RuleMatch> matches = new ArrayList<>();
    final RuleMatch localMatch = new RuleMatch();
    initMatch(localMatch, "local");
    matches.add(localMatch);
    final RuleMatch virtualMatch = new RuleMatch();
    if (!DOM.isNativeMode()) {
      initMatch(virtualMatch, "virtual");
      matches.add(virtualMatch);
    }

    result.matchedCSSRules = matches;// ListUtil.newImmutableList(match);

    mDocument.postAndWait(new Runnable() {
      @Override
      public void run() {
        Object elementForNodeId = mDocument.getElementForNodeId(request.nodeId);

        if (elementForNodeId == null) {
          LogUtil.w("Failed to get style of an element that does not exist, nodeid=" +
              request.nodeId);
          return;
        }

        mDocument.getElementStyles(
            elementForNodeId,
            new StyleAccumulator() {
              @Override
              public void store(String name, String value, boolean isDefault) {
                if (!isDefault) {
                  CSSProperty property = new CSSProperty();
                  if (!name.startsWith(W3CStyleConstants.V_PREFIX)) {
                    property.name = name;
                    property.value = value;
                    localMatch.rule.style.cssProperties.add(property);
                  } else {
                    if (!DOM.isNativeMode()) {
                      CSSProperty virtualProperty = new CSSProperty();
                      virtualProperty.name = name;
                      virtualProperty.value = value;
                      virtualMatch.rule.style.cssProperties.add(virtualProperty);
                    }
                  }
                }
              }
            });
      }
    });

    result.inherited = Collections.emptyList();
    result.pseudoElements = Collections.emptyList();

    return result;
  }

  private final class PeerManagerListener extends PeersRegisteredListener {
    @Override
    protected synchronized void onFirstPeerRegistered() {
      mDocument.addRef();
    }

    @Override
    protected synchronized void onLastPeerUnregistered() {
      mDocument.release();
    }
  }

  private static class CSSComputedStyleProperty {
    @JsonProperty(required = true)
    public String name;

    @JsonProperty(required = true)
    public String value;
  }

  private static class RuleMatch {
    @JsonProperty
    public CSSRule rule;

    @JsonProperty
    public List<Integer> matchingSelectors;
  }

  private static class SelectorList {
    @JsonProperty
    public List<Selector> selectors;

    @JsonProperty
    public String text;
  }

  private static class SourceRange {
    @JsonProperty(required = true)
    public int startLine;

    @JsonProperty(required = true)
    public int startColumn;

    @JsonProperty(required = true)
    public int endLine;

    @JsonProperty(required = true)
    public int endColumn;
  }

  private static class Selector {
    @JsonProperty(required = true)
    public String text;

    @JsonProperty
    public SourceRange range;
  }

  private static class CSSRule {
    @JsonProperty
    public String styleSheetId;

    @JsonProperty(required = true)
    public SelectorList selectorList;

    @JsonProperty
    public Origin origin;

    @JsonProperty
    public CSSStyle style;
  }

  private static class CSSStyle {
    @JsonProperty
    public String styleSheetId;

    @JsonProperty(required = true)
    public List<CSSProperty> cssProperties;

    @JsonProperty
    public List<ShorthandEntry> shorthandEntries;

    @JsonProperty
    public String cssText;

    @JsonProperty
    public SourceRange range;
  }

  private static class ShorthandEntry {
    @JsonProperty(required = true)
    public String name;

    @JsonProperty(required = true)
    public String value;

    @JsonProperty
    public Boolean important;
  }

  private static class CSSProperty {
    @JsonProperty(required = true)
    public String name;

    @JsonProperty(required = true)
    public String value;

    @JsonProperty
    public Boolean important;

    @JsonProperty
    public Boolean implicit;

    @JsonProperty
    public String text;

    @JsonProperty
    public Boolean parsedOk;

    @JsonProperty
    public Boolean disabled;

    @JsonProperty
    public SourceRange range;
  }

  private static class PseudoIdMatches {
    @JsonProperty(required = true)
    public int pseudoId;

    @JsonProperty(required = true)
    public List<RuleMatch> matches;

    public PseudoIdMatches() {
      this.matches = new ArrayList<>();
    }
  }

  private static class GetComputedStyleForNodeRequest {
    @JsonProperty(required = true)
    public int nodeId;
  }

  private static class InheritedStyleEntry {
    @JsonProperty(required = true)
    public CSSStyle inlineStyle;

    @JsonProperty(required = true)
    public List<RuleMatch> matchedCSSRules;
  }

  private static class GetComputedStyleForNodeResult implements JsonRpcResult {
    @JsonProperty(required = true)
    public List<CSSComputedStyleProperty> computedStyle;
  }

  private static class GetMatchedStylesForNodeRequest implements JsonRpcResult {
    @JsonProperty(required = true)
    public int nodeId;

    @JsonProperty
    public Boolean excludePseudo;

    @JsonProperty
    public Boolean excludeInherited;
  }

  private static class GetMatchedStylesForNodeResult implements JsonRpcResult {
    @JsonProperty
    public List<RuleMatch> matchedCSSRules;

    @JsonProperty
    public List<PseudoIdMatches> pseudoElements;

    @JsonProperty
    public List<InheritedStyleEntry> inherited;
  }

  private void initMatch(RuleMatch match, String value) {
    match.matchingSelectors = ListUtil.newImmutableList(0);

    Selector selector = new Selector();
    selector.text = value;

    CSSRule rule = new CSSRule();

    rule.origin = Origin.REGULAR;
    rule.selectorList = new SelectorList();

    rule.selectorList.selectors = ListUtil.newImmutableList(selector);

    rule.style = new CSSStyle();
    rule.style.cssProperties = new ArrayList<>();

    match.rule = rule;

    rule.style.shorthandEntries = Collections.emptyList();
  }
}
