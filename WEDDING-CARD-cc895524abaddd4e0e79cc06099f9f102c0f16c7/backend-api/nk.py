from pymongo import MongoClient

uri = "mongodb+srv://hotel_security:65JdbW2Xazplojmg@cluster0.7q1xysy.mongodb.net/?appName=Cluster0"
client = MongoClient(uri)

print(client.list_database_names())