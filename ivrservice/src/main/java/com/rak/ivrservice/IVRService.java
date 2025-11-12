package com.rak.ivrservice;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.google.cloud.texttospeech.v1.*;
import com.google.protobuf.ByteString;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class IVRService {

    private final String baseUrl = "http://localhost:8088/ari";
    private final RestTemplate restTemplate = new RestTemplate();
    private final Path ttsDir = Path.of("D:/Asterisk/sounds/tts");

    public IVRService() throws Exception {
        // Ensure directory exists
        Files.createDirectories(ttsDir);
        System.out.println("✅ TTS directory ready at: " + ttsDir);
    }

    public void onCallStart(String channelId) {
        speak(channelId, "Welcome to our service. Press 1 for Hindi, or 2 for English.");
    }

    public void onDtmf(String channelId, String digit) {
        if ("1".equals(digit)) {
            speak(channelId, "You selected Hindi.");
        } else if ("2".equals(digit)) {
            speak(channelId, "You selected English.");
        } else {
            speak(channelId, "Invalid input, please try again.");
        }
    }

    private void speak(String channelId, String text) {
        try {
            // 1️⃣ Generate a WAV file from Google TTS
            String baseName = "tts_" + System.currentTimeMillis();
            Path wavPath = ttsDir.resolve(baseName + ".wav");
            Path ulawPath = ttsDir.resolve(baseName + ".ulaw");

            synthesizeTextToFile(text, wavPath.toString());

            // 2️⃣ Convert WAV → ULAW (8kHz, mono) using ffmpeg
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y", "-i", wavPath.toString(),
                    "-ar", "8000", "-ac", "1", "-f", "mulaw", ulawPath.toString()
            );
            pb.inheritIO().start().waitFor();
            System.out.println("🎵 Converted WAV to ULAW: " + ulawPath);

            // 3️⃣ Build HTTP headers with ARI credentials
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth("myariuser", "myaripass");
            HttpEntity<String> request = new HttpEntity<>(headers);

            // 4️⃣ Tell Asterisk to play the sound file
            String mediaPath = "sound:tts/" + baseName;
            String playUrl = baseUrl + "/channels/" + channelId + "/play?media=" + mediaPath;

            ResponseEntity<String> response = restTemplate.exchange(playUrl, HttpMethod.POST, request, String.class);
            System.out.println("📢 Playing audio: " + mediaPath + " | Response: " + response.getStatusCode());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void synthesizeTextToFile(String text, String filePath) throws Exception {
        try (TextToSpeechClient textToSpeechClient = TextToSpeechClient.create()) {
            SynthesisInput input = SynthesisInput.newBuilder().setText(text).build();
            VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
                    .setLanguageCode("en-US")
                    .setSsmlGender(SsmlVoiceGender.NEUTRAL)
                    .build();

            AudioConfig audioConfig = AudioConfig.newBuilder()
                    .setAudioEncoding(AudioEncoding.LINEAR16) // WAV output
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
