package com.rak.ivrservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.texttospeech.v1.*;
import com.google.protobuf.ByteString;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IVRService {

    private final String baseUrl = "http://localhost:8088/ari";
    private final RestTemplate restTemplate = new RestTemplate();
    private final Path ttsDir = Path.of("D:/Asterisk/sounds/tts");
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode flow; // entire call flow JSON
    private final Map<String, String> callState = new ConcurrentHashMap<>(); // channelId -> currentNodeId

    public IVRService() throws Exception {
        Files.createDirectories(ttsDir);
        loadFlow();
        preGenerateTTS(flow); // optional: preload existing prompts
    }

    private void loadFlow() throws Exception {
        flow = mapper.readTree(new File("D:/STUDY/GIT/Call-Handling/basiccallflow.json"));
    }

    /** Pre-generate TTS for all nodes recursively (optional) */
    private void preGenerateTTS(JsonNode node) throws Exception {
        if (node.has("prompt")) {
            String prompt = node.get("prompt").asText();
            String lang = node.has("language") ? node.get("language").asText() : "en-US";
            generateTTSIfAbsent(prompt, lang);
        }
        if (node.has("next")) {
            for (JsonNode nextNode : node.get("next")) {
                preGenerateTTS(flow.get(nextNode.asText()));
            }
        }
    }

    /** Handle new call start */
    public void onCallStart(String channelId) {
        callState.put(channelId, "start");
        playCurrentPrompt(channelId);
    }

    /** Handle DTMF dynamically */
    public void onDtmf(String channelId, String digit) {
        String currentNodeId = callState.get(channelId);
        if (currentNodeId == null) return;

        JsonNode currentNode = flow.get(currentNodeId);

        if (currentNode.has("next") && currentNode.get("next").has(digit)) {
            String nextNodeId = currentNode.get("next").get(digit).asText();
            callState.put(channelId, nextNodeId);
            playCurrentPrompt(channelId);
        } else {
            System.out.println("❌ Invalid input: " + digit);
            playPrompt(channelId, "Invalid input, please try again.", "en-US");
        }
    }

    /** Play prompt for current node */
    private void playCurrentPrompt(String channelId) {
        String currentNodeId = callState.get(channelId);
        JsonNode node = flow.get(currentNodeId);
        String text = node.get("prompt").asText();
        String lang = node.has("language") ? node.get("language").asText() : "en-US";

        try {
            // Dynamically generate TTS if missing
            generateTTSIfAbsent(text, lang);
        } catch (Exception e) {
            e.printStackTrace();
        }

        playPrompt(channelId, text, lang);
    }

    /** Play audio via ARI */
    private void playPrompt(String channelId, String text, String languageCode) {
        try {
            String baseName = "tts_" + text.hashCode() + "_" + languageCode;
            String mediaPath = "sound:tts/" + baseName;

            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth("myariuser", "myaripass");
            HttpEntity<String> request = new HttpEntity<>(headers);

            String playUrl = baseUrl + "/channels/" + channelId + "/play?media=" + mediaPath;
            restTemplate.exchange(playUrl, HttpMethod.POST, request, String.class);

            System.out.println("📢 Playing audio: " + mediaPath + " | Channel: " + channelId);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Generate TTS only if missing */
    private void generateTTSIfAbsent(String text, String languageCode) throws Exception {
        String baseName = "tts_" + text.hashCode() + "_" + languageCode;
        Path ulawPath = ttsDir.resolve(baseName + ".ulaw");

        if (Files.exists(ulawPath)) return; // Already exists

        Path wavPath = ttsDir.resolve(baseName + ".wav");
        synthesizeTextToFile(text, wavPath.toString(), languageCode);

        // Convert WAV → ULAW
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y", "-i", wavPath.toString(),
                "-ar", "8000", "-ac", "1", "-f", "mulaw", ulawPath.toString()
        );
        pb.inheritIO().start().waitFor();

        System.out.println("🎵 TTS generated dynamically: " + ulawPath);
    }

    /** Call Google TTS */
    private void synthesizeTextToFile(String text, String filePath, String languageCode) throws Exception {
        try (TextToSpeechClient client = TextToSpeechClient.create()) {
            SynthesisInput input = SynthesisInput.newBuilder().setText(text).build();
            VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
                    .setLanguageCode(languageCode)
                    .setSsmlGender(SsmlVoiceGender.NEUTRAL)
                    .build();
            AudioConfig audioConfig = AudioConfig.newBuilder()
                    .setAudioEncoding(AudioEncoding.LINEAR16)
                    .build();
            SynthesizeSpeechResponse response = client.synthesizeSpeech(input, voice, audioConfig);
            ByteString audioContents = response.getAudioContent();
            try (FileOutputStream out = new FileOutputStream(filePath)) {
                out.write(audioContents.toByteArray());
            }
        }
    }
}
