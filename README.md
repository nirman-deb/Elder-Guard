# 🛡️ ElderGuard

ElderGuard is an **Invisible, AI-Powered Guardian** designed specifically for elderly and non-tech-savvy smartphone users. It proactively blocks social engineering scams, phishing links, and malicious sideloaded APKs using an on-device Retrieval-Augmented Generation (RAG) pipeline and low-level OS scanning, all without requiring any active user intervention.

---

## ⚠️ IMPORTANT NOTE FOR JUDGES: API KEY SETUP
To test this application locally, you **MUST** provide your own Gemini API key. For security reasons, the API key has been removed from the repository.

**Steps to configure the API key:**
1. Open the project in Android Studio.
2. Open the `local.properties` file (located in the root directory).
3. Add the following line at the bottom of the file:
   ```properties
   GEMINI_API_KEY="<Dear judges please Enter your Gemini API key here>"
4. Sync the project with Gradle files and Run the app.
(Note: For the live hackathon demo, we have included a hardcoded fallback for a specific mock URL https://hdfc-urgent-kyc.com/update to demonstrate the AI block screen without relying on live, rapidly-changing phishing servers that frequently go offline.)
🚨 The Problem It Solves
While smartphones are reaching every demographic, digital literacy is lagging. Older adults are disproportionately targeted by sophisticated cyber frauds (e.g., fake KYC updates, electricity disconnection threats). Existing security solutions fail them because they:
Require complex setups and technical literacy.
Rely on outdated, easily bypassable keyword blocklists (resulting in high false positives).
Cannot detect modern, highly-cloned phishing UI pages.
💡 Key Features & Innovations
1. Context-Aware Link Scanning (On-Device Mini-RAG)
Instead of blindly flagging keywords, ElderGuard intercepts incoming messages and shared links. It silently makes a background Jsoup request to the URL, extracting critical metadata (Open Graph tags, hidden <form> submission targets, canonical links, and empty link ratios). This context is fed to Gemini 2.5 Flash, enabling the AI to accurately distinguish between a real bank portal and a phishing trap.
2. URL Sanitization (Blind Prompting)
To completely eliminate LLM hallucinations and the "Negative Prompting" trap (where AI flags safe links just because they contain the word "scam"), the app scrubs the actual URL from the prompt before sending it to Gemini. The AI relies strictly on the extracted metadata to make its decision.
3. Proactive Sideloaded Malware Defense
Bypassing modern Android scoped-storage restrictions, ElderGuard utilizes Kotlin Coroutines for aggressive background polling. It scans the device for newly downloaded, uninstalled APKs and parses their AndroidManifest.xml to flag highly dangerous permission combinations (like SMS reading or Screen Overlays) before the user can install them.
4. Tech SOS Alerts
When a severe threat is detected, ElderGuard doesn't just block the screen; it instantly sends an SMS alert to a pre-configured, tech-savvy family member, providing peace of mind for families living apart.
🛠️ Technologies Used
Android Studio & Kotlin: Core development.
Jetpack Compose: Modern, declarative UI framework.
Gemini 2.5 Flash API: The brain behind the threat analysis.
Jsoup: For real-time background HTML parsing and metadata extraction (Mini-RAG).
Kotlin Coroutines & Dispatchers.IO: For asynchronous background polling and safe network calls without freezing the main thread.
Android Services: Utilizing NotificationListenerService and Accessibility services for invisible background protection.
⚙️ How to Run
Clone the repository: git clone https://github.com/your-username/ElderGuard.git
Open the project in Android Studio.
Add your GEMINI_API_KEY in the local.properties file (as instructed above).
Build and run on an Android device or Emulator (API level 28+ recommended).
Follow the on-boarding steps to grant the necessary permissions (Notification Access, File Access, SMS).
To Test: Share a link from your browser to the app, or send an SMS containing a sketchy link to your emulator to trigger the AI scanner.
   
