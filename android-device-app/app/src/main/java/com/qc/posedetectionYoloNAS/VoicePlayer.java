//============================================================================
// Vyāyāma — VoicePlayer: on-device Text-to-Speech for the voice coach. OFFLINE
// ONLY (network voices are filtered out → preserves the "nothing leaves the
// device" guarantee; needs no INTERNET permission). Soft female default voice,
// tunable rate/pitch, voice selection. QUEUE_FLUSH so a new cue never overlaps.
//============================================================================
package com.qc.posedetectionYoloNAS;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class VoicePlayer implements TextToSpeech.OnInitListener {

    private TextToSpeech tts;
    private volatile boolean ready = false;
    private float rate, pitch;
    private String desiredVoiceName;                 // from prefs; null = auto-pick female
    private final List<Voice> offlineVoices = new ArrayList<>();   // for the settings picker
    private int utterCounter = 0;

    public VoicePlayer(Context ctx, float rate, float pitch, String voiceName) {
        this.rate = rate; this.pitch = pitch; this.desiredVoiceName = voiceName;
        this.tts = new TextToSpeech(ctx.getApplicationContext(), this);  // applicationContext: outlives the fragment
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) { ready = false; return; }
        try {
            int lang = tts.setLanguage(Locale.US);
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.getDefault());    // don't abort — many engines still speak
            }
            tts.setSpeechRate(rate);
            tts.setPitch(pitch);

            Set<Voice> vs = null;
            try { vs = tts.getVoices(); } catch (Exception e) { vs = null; }  // can return null OR throw
            offlineVoices.clear();
            if (vs != null) {
                for (Voice v : vs) {
                    if (v == null) continue;
                    if (v.isNetworkConnectionRequired()) continue;            // OFFLINE ONLY — preserves no-network guarantee
                    Locale loc = v.getLocale();
                    if (loc != null && "en".equalsIgnoreCase(loc.getLanguage())) offlineVoices.add(v);
                }
            }
            applyVoiceSelection();
            ready = true;
        } catch (Exception e) {
            ready = false;
        }
    }

    private void applyVoiceSelection() {
        try {
            if (desiredVoiceName != null) {
                for (Voice v : offlineVoices) if (desiredVoiceName.equals(v.getName())) { tts.setVoice(v); return; }
            }
            Voice female = null, highQ = null, first = null;
            for (Voice v : offlineVoices) {
                if (first == null) first = v;
                String name = v.getName() == null ? "" : v.getName().toLowerCase(Locale.US);
                if (female == null && name.contains("female")) female = v;
                if (highQ == null && v.getQuality() >= Voice.QUALITY_NORMAL) highQ = v;
            }
            Voice pick = (female != null) ? female : (highQ != null ? highQ : first);
            if (pick != null) tts.setVoice(pick);   // else leave engine default
        } catch (Exception ignored) {}
    }

    public void speak(String line) {
        TextToSpeech t = tts;   // snapshot once — guards against a shutdown() on another thread nulling the field mid-call
        if (!ready || t == null || line == null || line.isEmpty()) return;
        t.speak(line, TextToSpeech.QUEUE_FLUSH, null, "vc" + (utterCounter++));   // QUEUE_FLUSH: a new cue replaces a playing one
    }

    public boolean isReady() { return ready; }
    public boolean isSpeaking() { return tts != null && tts.isSpeaking(); }
    public void stopIfSpeaking() { try { if (tts != null) tts.stop(); } catch (Exception ignored) {} }

    // ---- settings-support (called from VoiceSettingsDialog) ----
    public void setRate(float r) { rate = r; if (ready && tts != null) try { tts.setSpeechRate(r); } catch (Exception ignored) {} }
    public void setVoiceByName(String name) {
        desiredVoiceName = name;
        if (ready && tts != null) { for (Voice v : offlineVoices) if (name != null && name.equals(v.getName())) { try { tts.setVoice(v); } catch (Exception ignored) {} return; } }
    }
    public List<Voice> getOfflineEnVoices() { return new ArrayList<>(offlineVoices); }   // copy; empty until ready
    public void test() { speak("Nice work. Let's keep that form."); }

    public void shutdown() {
        if (tts != null) { try { tts.stop(); tts.shutdown(); } catch (Exception ignored) {} tts = null; }
        ready = false;
    }
}
