package com.focusdesktop.infrastructure.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;

final class BrowserProxyPolicy {
    static final String ABSENT = "__ABSENT__";
    static final ObjectMapper JSON = new ObjectMapper();
    static String pac(String value) {
        URI uri = URI.create(value);
        if (!"http".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost())
                || uri.getPort() < 1024 || uri.getPort() > 65535 || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null
                || !uri.getPath().matches("/pac/[0-9a-f-]{36}")) throw new IllegalArgumentException("Invalid local PAC");
        return value;
    }
    static String chromium(String pac) {
        var node = JSON.createObjectNode();
        node.putObject("ProxySettings").put("ProxyMode", "pac_script")
                .put("ProxyPacUrl", pac(pac)).put("ProxyPacMandatory", false);
        return node.toString();
    }
    static JsonNode parse(String value) {
        try { JsonNode node = JSON.readTree(value); if (node == null || !node.isObject()) throw new IllegalArgumentException(); return node; }
        catch (java.io.IOException exception) { throw new IllegalArgumentException("Invalid browser policy", exception); }
    }
}
