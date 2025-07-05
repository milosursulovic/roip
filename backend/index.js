// import fs from "fs";
// import https from "https";
import express from "express";
import http from "http";
import { Server } from "socket.io";
import cors from "cors";
import dotenv from "dotenv";
import authRoutes from "./routes/auth.js";
import setupSignaling from "./utils/webrtc-signaling.js";
import { connectDB } from "./config/db.js";

dotenv.config();

const app = express();
const server = http.createServer(app);

// const sslOptions = {
//   key: fs.readFileSync(process.env.CERT_KEY_PATH),
//   cert: fs.readFileSync(process.env.CERT_PATH),
// };

// const httpsServer = https.createServer(sslOptions, app);

const io = new Server(server, {
  cors: {
    origin: "*",
    methods: ["GET", "POST"],
  },
});

app.use(cors());
app.use(express.json());
app.use("/api/auth", authRoutes);

setupSignaling(io);

const PORT = process.env.PORT || 3000;
const HOST = process.env.HOST || "localhost";

await connectDB();

server.listen(PORT, () =>
  console.log(`🚀 HTTPS server running at http://${HOST}:${PORT}`)
);
