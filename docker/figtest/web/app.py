from flask import Flask
from redis import Redis
import os
print os.environ, "<--"
app = Flask(__name__)
redis = Redis(host=os.environ.get("REDIS_PORT_6379_TCP_ADDR"),
              port=os.environ.get("REDIS_PORT_6379_TCP_PORT"))

@app.route('/')
def hello():
    redis.incr('hits')
    redis.save()
    return 'Hello... I have been seen %s times.' % redis.get('hits')

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=80, debug=True)