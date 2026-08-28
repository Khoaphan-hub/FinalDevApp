# AI Service Setup Instructions

## Overview
The AI service provides intelligent chatbot functionality for the Dalat tourism project. It uses sentence transformers for semantic search and Google's Gemini AI for generating responses.

## Features
- Semantic search through POI and Eatery data
- Intelligent query transformation and translation
- Context-aware responses using RAG (Retrieval Augmented Generation)
- Chat history integration with multiple format support
- Vietnamese language support
- Graceful fallback when AI dependencies are missing

## Installation

### 1. Install Required Packages
```bash
pip install -r requirements.txt
```

The key AI packages are:
- `sentence-transformers`: For semantic search and embeddings
- `google-generativeai`: For Gemini AI integration  
- `torch`: Required by sentence-transformers

### 2. Configure Gemini API Key
Add your Gemini API key to `firstsite/settings.py`:
```python
GEMINI_API_KEY = "your_api_key_here"
```

## Chat History Format

The AI service accepts multiple chat history formats for maximum compatibility:

### Supported Formats

1. **Simple Format** (recommended for frontend):
```json
[
  {"user": "Hello", "bot": "Hi there!"},
  {"user": "How are you?", "bot": "I'm doing well!"}
]
```

2. **Gemini API Format**:
```json
[
  {"role": "user", "parts": [{"text": "Hello"}]},
  {"role": "model", "parts": [{"text": "Hi there!"}]},
  {"role": "user", "parts": [{"text": "How are you?"}]},
  {"role": "model", "parts": [{"text": "I'm doing well!"}]}
]
```

3. **Alternative Format**:
```json
[
  {"message": "Hello", "response": "Hi there!"}
]
```

The service automatically converts any of these formats to the required Gemini API format internally.

## Usage

### 1. Data Loading
The AI service automatically loads and vectorizes POI and Eatery data when Django starts:
- Data is cached in `chatbot_vectors.pkl` for faster subsequent loads
- Cache is automatically refreshed when database changes

### 2. Chatbot Integration  
The service is integrated through the ChatAPI view:
```python
from home import ai_service

# Get AI response
response = ai_service.find_best_answer(user_query, chat_history)
```

### 3. Testing
Run the test script to verify functionality:
```bash
python test_ai_service.py
python test_chat_fix.py
```

## Architecture

### Data Models
- **Poi**: Points of interest (attractions, landmarks)
- **Eatery**: Restaurants and food establishments

### Key Functions
- `load_data_and_vectorize()`: Loads and processes data for search
- `find_best_answer()`: Main function for generating responses
- `_transform_query()`: Optimizes and translates queries
- `_is_general_knowledge_query()`: Classifies query intent
- `_convert_chat_history_to_gemini_format()`: Converts chat history formats

### Search Document Creation
- `_create_poi_search_document()`: Creates searchable text for POIs
- `_create_eatery_search_document()`: Creates searchable text for eateries
- `_create_poi_formatted_answer()`: Formats POI information for responses
- `_create_eatery_formatted_answer()`: Formats eatery information for responses

## Error Handling
The service gracefully handles missing dependencies and API failures:
- Returns informative error messages when AI is unavailable
- Falls back to general knowledge when specific data isn't found
- Logs detailed error information for debugging
- Handles multiple chat history formats automatically

## Performance Notes
- Initial data loading may take 1-2 minutes
- Subsequent requests are fast due to caching
- Vector similarity search is optimized for real-time responses
- Background loading prevents blocking the Django startup

## Troubleshooting

### Common Issues
1. **Import errors**: Ensure all packages in requirements.txt are installed
2. **API key errors**: Check that GEMINI_API_KEY is properly set
3. **Empty responses**: Verify that POI/Eatery data exists in database
4. **Slow responses**: Check if data needs to be re-vectorized
5. **Chat history errors**: Ensure proper format (see supported formats above)

### Fixed Issues
- ✅ **Chat History Format Error**: The service now properly handles different chat history formats and converts them to Gemini API compatible format
- ✅ **Model Import Error**: Updated to use correct Poi and Eatery models
- ✅ **Missing Dependencies**: Added graceful fallbacks when AI packages aren't installed

### Debug Mode
Enable debug logging in Django settings to see detailed AI service logs.