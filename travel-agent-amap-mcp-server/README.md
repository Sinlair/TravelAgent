# Amap MCP Server

This module exposes Amap weather and geospatial capabilities as a standalone MCP server over Streamable HTTP.

## Tools

- `amap_weather`
- `amap_geocode`
- `amap_reverse_geocode`
- `amap_input_tips`
- `amap_transit_route`

## Run

```bash
TRAVEL_AGENT_AMAP_API_KEY="<your-amap-web-service-key>" ./mvnw -pl travel-agent-amap-mcp-server -am spring-boot:run
```

Endpoint:

- `http://localhost:8090/mcp`

## Manual Smoke Test

The Streamable HTTP transport expects `Accept: text/event-stream, application/json`.

Initialize a session:

```bash
curl -i \
  -H "Accept: text/event-stream, application/json" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"init-1","method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"manual-test","version":"1.0.0"}}}' \
  http://localhost:8090/mcp
```

Use the returned `Mcp-Session-Id` header in later requests.

List tools:

```bash
curl -i \
  -H "Accept: text/event-stream, application/json" \
  -H "Content-Type: application/json" \
  -H "Mcp-Session-Id: <session-id>" \
  -d '{"jsonrpc":"2.0","id":"tools-1","method":"tools/list","params":{}}' \
  http://localhost:8090/mcp
```

Call weather:

```bash
curl -i \
  -H "Accept: text/event-stream, application/json" \
  -H "Content-Type: application/json" \
  -H "Mcp-Session-Id: <session-id>" \
  -d '{"jsonrpc":"2.0","id":"call-1","method":"tools/call","params":{"name":"amap_weather","arguments":{"city":"330100"}}}' \
  http://localhost:8090/mcp
```
