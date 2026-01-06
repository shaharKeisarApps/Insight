# MetroDITest

An expense tracking Android app built with Metro DI, Circuit, and Jetpack Compose.

## AI Features Setup (Required for AI functionality)

This app includes AI-powered features using the Koog framework:
- **Smart Categorization**: AI suggests expense categories based on description
- **Financial Insights Chat**: Ask questions about your spending patterns

### Enable AI Features

1. Get an OpenAI API key from https://platform.openai.com/api-keys

2. Add your API key to `local.properties` (create the file if it doesn't exist):
   ```properties
   OPENAI_API_KEY=sk-your-key-here
   ```

3. Rebuild the app:
   ```bash
   ./gradlew clean assembleDebug
   ```

**Note:** AI features are automatically disabled when no API key is provided. The app will show a message prompting you to add the key.

## Build

```bash
./gradlew assembleDebug
```

## Test

```bash
./gradlew test
```
