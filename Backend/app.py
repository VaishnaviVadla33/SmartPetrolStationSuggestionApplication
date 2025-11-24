from flask import Flask, jsonify, request
from register_user import register_user

app = Flask(__name__)

@app.route("/hello", methods=["GET"])
def hello():
    return jsonify({"message": "Hello from Python Backend!"})

@app.route("/register", methods=["POST"])
def register():
    data = request.get_json(silent=True) or {}
    car_id = data.get("car_id")
    dongle_mac = data.get("dongle_mac")
    android_id = data.get("android_id")
    mode = data.get("mode") or "manual"
    public_key = data.get("public_key")
    user_name = data.get("user_name")  # <-- NEW: Get user_name

    # Validate required fields
    missing = []
    if not car_id:
        missing.append("car_id")
    if not dongle_mac:
        missing.append("dongle_mac")
    if not android_id:
        missing.append("android_id")
    if not public_key:
        missing.append("public_key")
    if not user_name:  # <-- NEW: Validate user_name
        missing.append("user_name")

    if missing:
        return jsonify({
            "status": "error",
            "message": f"Missing required fields: {', '.join(missing)}"
        }), 400

    # Register user with all details
    result = register_user(car_id, dongle_mac, android_id, mode, public_key, user_name)
    
    status_code = 200 if result.get("status") == "success" else 400
    return jsonify(result), status_code

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)