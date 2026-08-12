# RJNX AI V1 — AI Connected

## API key setup
Do NOT hard-code the key into source code.

Add this to your Gradle user properties (`~/.gradle/gradle.properties`):

`OPENAI_API_KEY=YOUR_KEY_HERE`

Then build the debug APK.

The app sends general questions to the OpenAI Responses API and reads the returned text aloud. Phone commands such as YouTube, Settings, Gallery, Google search and notes continue to work locally.

### Important for publishing
A production app should NOT ship a permanent OpenAI API key inside the APK. Use a small backend/proxy that keeps the key on the server, authenticates your users, and applies rate limits.
