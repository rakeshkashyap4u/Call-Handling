package com.rak.ivrservice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.annotation.PostConstruct;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import com.fasterxml.jackson.databind.ObjectMapper;
@Service
public class AsteriskARIClient {
    private static final String ARI_URL = "ws://localhost:8088/ari/events?api_key=myariuser:myaripass&app=myivr";
    private final OkHttpClient client = new OkHttpClient();
    
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private IVRService ivrService;
    

    @PostConstruct
    public void connect() {
        Request request = new Request.Builder().url(ARI_URL).build();
        client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response res) {
                System.out.println("✅ Connected to Asterisk ARI WebSocket");
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                try {
                    JsonNode event = mapper.readTree(text);
                    String type = event.get("type").asText();

                    if ("StasisStart".equals(type)) {
                        String channelId = event.get("channel").get("id").asText();
                        System.out.println("📞 Call started on channel: " + channelId);
                        ivrService.onCallStart(channelId);
                    }

                    if ("ChannelDtmfReceived".equals(type)) {
                        String digit = event.get("digit").asText();
                        String channelId = event.get("channel").get("id").asText();
                        System.out.println("🔢 DTMF pressed: " + digit);
                        ivrService.onDtmf(channelId, digit);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response res) {
                System.err.println("❌ WebSocket error: " + t.getMessage());
            }
        });
    }

    private String extractChannelId(String json) {
        int idx = json.indexOf("\"channel\":{\"id\":\"");
        if (idx == -1) return null;
        int start = idx + 17;
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }

    private String extractDigit(String json) {
        int idx = json.indexOf("\"digit\":\"");
        if (idx == -1) return "";
        int start = idx + 9;
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}
