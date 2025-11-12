package com.rak.ivrservice;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.google.cloud.texttospeech.v1.*;
import com.google.protobuf.ByteString;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
public class IVRService {

    private final String baseUrl = "http://localhost:8088/ari";
    private final RestTemplate restTemplate = new RestTemplate();
    private final Path ttsDir = Path.of("D:/Asterisk/sounds/tts");

    // Cache: text+language → base file name
    private final Map<String, String> ttsCache = new ConcurrentHashMap<>();

    public IVRService() throws Exception {
        Files.createDirectories(ttsDir);
        System.out.println("✅ TTS directory ready at: " + ttsDir);
    }

    public void onCallStart(String channelId) {
        speak(channelId, "Welcome to our service. Press 1 for English, or 2 for Hindi.", "en-US");
    }

    public void onDtmf(String channelId, String digit) {
        if ("1".equals(digit)) {
            speak(channelId, "You selected English. Thank you for calling.", "en-US");
        } else if ("2".equals(digit)) {
            speak(channelId, "Aap ne Hindi bhasa chuna hai. Call karne ke liye dhanyawaad.", "hi-IN");
        } else {
            speak(channelId, "Invalid input, please try again.", "en-US");
        }
    }

    private void speak(String channelId, String text, String languageCode) {
        try {
            // 1️⃣ Check if TTS already exists in cache
            String cacheKey = languageCode + ":" + text;
            String baseName = ttsCache.get(cacheKey);

            Path wavPath;
            Path ulawPath;

            if (baseName != null) {
                // Use cached file
                wavPath = ttsDir.resolve(baseName + ".wav");
                ulawPath = ttsDir.resolve(baseName + ".ulaw");

                if (!Files.exists(ulawPath)) {
                    // Regenerate .ulaw if missing
                    convertWavToUlaw(wavPath, ulawPath);
                }
            } else {
                // 2️⃣ Generate new TTS
                baseName = "tts_" + System.currentTimeMillis();
                wavPath = ttsDir.resolve(baseName + ".wav");
                ulawPath = ttsDir.resolve(baseName + ".ulaw");

                synthesizeTextToFile(text, wavPath.toString(), languageCode);
                convertWavToUlaw(wavPath, ulawPath);

                // Save to cache
                ttsCache.put(cacheKey, baseName);
            }

            // 3️⃣ Play via ARI
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth("myariuser", "myaripass");
            HttpEntity<String> request = new HttpEntity<>(headers);

            String mediaPath = "sound:tts/" + baseName;
            String playUrl = baseUrl + "/channels/" + channelId + "/play?media=" + mediaPath;

            ResponseEntity<String> response = restTemplate.exchange(playUrl, HttpMethod.POST, request, String.class);
            System.out.println("📢 Playing audio: " + mediaPath + " | Response: " + response.getStatusCode());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void convertWavToUlaw(Path wavPath, Path ulawPath) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-y", "-i", wavPath.toString(),
                "-ar", "8000", "-ac", "1", "-f", "mulaw", ulawPath.toString());
        pb.inheritIO().start().waitFor();
        System.out.println("🎵 Converted WAV to ULAW: " + ulawPath);
    }

    private void synthesizeTextToFile(String text, String filePath, String languageCode) throws Exception {
        try (TextToSpeechClient textToSpeechClient = TextToSpeechClient.create()) {
            SynthesisInput input = SynthesisInput.newBuilder().setText(text).build();
            VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
                    .setLanguageCode(languageCode)
                    .setSsmlGender(SsmlVoiceGender.NEUTRAL)
                    .build();
            AudioConfig audioConfig = AudioConfig.newBuilder()
                    .setAudioEncoding(AudioEncoding.LINEAR16) // WAV
                    .build();

            SynthesizeSpeechResponse response = textToSpeechClient.synthesizeSpeech(input, voice, audioConfig);
            ByteString audioContents = response.getAudioContent();

            try (FileOutputStream out = new FileOutputStream(filePath)) {
                out.write(audioContents.toByteArray());
                System.out.println("✅ TTS audio saved at: " + filePath);
            }
        }
    }
}
