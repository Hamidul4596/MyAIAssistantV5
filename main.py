import os, base64
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from openai import OpenAI

load_dotenv()
app = FastAPI(title="My AI Assistant V4")
MODEL = os.getenv("OPENAI_MODEL", "gpt-5.6")
client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))

_allowed_origins = os.getenv("ALLOWED_ORIGINS", "*")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"] if _allowed_origins.strip() == "*" else [o.strip() for o in _allowed_origins.split(",")],
    allow_methods=["*"],
    allow_headers=["*"],
)

SYSTEM = """You are a safe Android personal AI assistant.
Reply naturally in Bengali when the user speaks Bengali, otherwise use the user's language.
Never claim an action happened unless the app confirms it.
For risky actions, explain that confirmation is required.
Do not provide arbitrary shell execution instructions as an action.
"""

class Command(BaseModel):
    text: str
    history: list[dict] = []

@app.get("/health")
def health():
    return {"ok": True, "version": "v4"}

@app.post("/command")
def command(req: Command):
    if not os.getenv("OPENAI_API_KEY"):
        raise HTTPException(500, "OPENAI_API_KEY is not configured on the server")
    messages = [{"role":"system","content":SYSTEM}]
    messages += req.history[-10:]
    messages.append({"role":"user","content":req.text})
    try:
        r = client.responses.create(model=MODEL, input=messages)
    except Exception as e:
        raise HTTPException(502, f"AI provider error: {e}")
    return {"reply": r.output_text, "version": "v4"}

class ScreenRequest(BaseModel):
    image_base64: str
    prompt: str = "Describe what is visible and suggest safe next steps."

@app.post("/analyze-screen")
def analyze_screen(req: ScreenRequest):
    if not os.getenv("OPENAI_API_KEY"):
        raise HTTPException(500, "OPENAI_API_KEY is not configured on the server")
    try:
        data = base64.b64decode(req.image_base64)
    except Exception:
        raise HTTPException(400, "Invalid base64 image")
    import base64 as b64
    image_url = "data:image/png;base64," + b64.b64encode(data).decode()
    try:
        r = client.responses.create(
            model=MODEL,
            input=[{"role":"user","content":[
                {"type":"input_text","text":req.prompt},
                {"type":"input_image","image_url":image_url}
            ]}]
        )
    except Exception as e:
        raise HTTPException(502, f"AI provider error: {e}")
    return {"reply": r.output_text}
