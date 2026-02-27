import os
import urllib.request
import json

api_key = os.environ.get("GEMINI_API_KEY")
url = f"https://generativelanguage.googleapis.com/v1beta/models?key={api_key}"
try:
    req = urllib.request.Request(url)
    with urllib.request.urlopen(req) as response:
        models = json.loads(response.read().decode())
        for model in models.get("models", []):
            if "thinking" in model["name"] or "exp" in model["name"]:
                print(f"{model['name']} - {model.get('supportedGenerationMethods', [])}")
except Exception as e:
    print(f"Error: {e}")
