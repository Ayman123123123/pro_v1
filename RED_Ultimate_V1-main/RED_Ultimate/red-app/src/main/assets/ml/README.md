# ML Models Directory

This directory contains on-device ML models for RED Ultimate.

## Required Models (Download at Runtime)

### Translation (ML Kit)
- Model: `translate/` - Auto-downloaded by ML Kit Translate API
- Languages: Arabic, English, and 100+ supported languages

### Text Recognition (ML Kit)
- Model: `text_recognition/` - Auto-downloaded by ML Kit Text Recognition API
- Scripts: Latin, Arabic, Chinese, Devanagari, Japanese, Korean

### Smart Reply (ML Kit)
- Model: `smart_reply/` - Auto-downloaded by ML Kit Smart Reply API
- Language: Arabic, English

### Content Classification (TensorFlow Lite)
- Model: `content_classifier.tflite` - Custom model for content moderation
- Labels: spam, harassment, violence, adult, safe

### Spam Detection (TensorFlow Lite)
- Model: `spam_detector.tflite` - Custom model for message spam detection
- Features: TF-IDF + neural network

### Face Detection (ML Kit)
- Model: `face_detection/` - Auto-downloaded by ML Kit Face Detection API
- Features: Face landmarks, contours, classification

## Download Instructions

Models are downloaded automatically on first use via ML Kit / TensorFlow Lite APIs.
For offline use, pre-bundle models in this directory and configure local model paths.

### TensorFlow Lite Models (Manual Download)
```bash
# Content Classifier
curl -L -o content_classifier.tflite "https://storage.googleapis.com/red-ml-models/content_classifier.tflite"

# Spam Detector
curl -L -o spam_detector.tflite "https://storage.googleapis.com/red-ml-models/spam_detector.tflite"
```

## Model Versions
- ML Kit Translate: 17.0.1
- ML Kit Text Recognition: 16.0.0
- ML Kit Smart Reply: 16.0.0
- ML Kit Face Detection: 16.1.6
- TensorFlow Lite: 2.16.1
- TensorFlow Lite GPU: 2.16.1