# Amap MCP Server

This module exposes Amap weather and geospatial capabilities as a standalone MCP server over Streamable HTTP.

## Tools

- `amap_weather`
- `amap_geocode`
- `amap_reverse_geocode`
- `amap_input_tips`
- `amap_transit_route`

## Run

```powershell
$env:TRAVEL_AGENT_AMAP_API_KEY = "<your-amap-web-service-key>"
.\mvnw.cmd -pl travel-agent-amap-mcp-server -am spring-boot:run
```

Endpoint:

- `http://localhost:8090/mcp`

## Manual Smoke Test

The Streamable HTTP transport expects `Accept: text/event-stream, application/json`.

```powershell
$accept = "text/event-stream, application/json"

$initBody = @{
  jsonrpc = "2.0"
  id = "init-1"
  method = "initialize"
  params = @{
    protocolVersion = "2025-03-26"
    capabilities = @{}
    clientInfo = @{
      name = "manual-test"
      version = "1.0.0"
    }
  }
} | ConvertTo-Json -Depth 6

$init = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "http://localhost:8090/mcp" -Headers @{ Accept = $accept } -ContentType "application/json" -Body $initBody
$sessionId = $init.Headers["Mcp-Session-Id"]

$notifyBody = @{ jsonrpc = "2.0"; method = "notifications/initialized" } | ConvertTo-Json -Depth 3
Invoke-WebRequest -UseBasicParsing -Method Post -Uri "http://localhost:8090/mcp" -Headers @{ Accept = $accept; "Mcp-Session-Id" = $sessionId } -ContentType "application/json" -Body $notifyBody | Out-Null
```

List tools:

```powershell
$listBody = @{
  jsonrpc = "2.0"
  id = "tools-1"
  method = "tools/list"
  params = @{}
} | ConvertTo-Json -Depth 4

Invoke-WebRequest -UseBasicParsing -Method Post -Uri "http://localhost:8090/mcp" -Headers @{ Accept = $accept; "Mcp-Session-Id" = $sessionId } -ContentType "application/json" -Body $listBody
```

Call weather:

```powershell
$callBody = @{
  jsonrpc = "2.0"
  id = "call-1"
  method = "tools/call"
  params = @{
    name = "amap_weather"
    arguments = @{
      city = "330100"
    }
  }
} | ConvertTo-Json -Depth 6

Invoke-WebRequest -UseBasicParsing -Method Post -Uri "http://localhost:8090/mcp" -Headers @{ Accept = $accept; "Mcp-Session-Id" = $sessionId } -ContentType "application/json" -Body $callBody
```

If you want to test Chinese input such as `天安门`, send the request body as UTF-8 bytes.
