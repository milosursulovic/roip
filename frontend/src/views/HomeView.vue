<template>
  <div
    class="min-h-screen bg-gray-100 flex flex-col items-center justify-center px-4"
  >
    <div class="bg-white shadow-xl rounded-xl p-6 w-full max-w-md">
      <h1 class="text-2xl font-bold text-center mb-4">📡 ROIP Push-to-Talk</h1>

      <div v-if="!joined" class="space-y-4">
        <input
          v-model="roomId"
          type="text"
          placeholder="Enter channel name"
          class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring"
        />
        <button
          class="w-full bg-blue-600 text-white py-2 rounded-lg hover:bg-blue-700"
          @click="joinRoom"
        >
          Join Channel
        </button>
      </div>

      <div v-else class="space-y-4 text-center">
        <p class="font-semibold text-lg">
          ✅ Connected to: <strong>{{ roomId }}</strong>
        </p>

        <button
          class="bg-green-600 text-white text-xl px-6 py-3 rounded-full hover:bg-green-700 active:scale-95 transition"
          @mousedown="startTalking"
          @mouseup="stopTalking"
          @touchstart.prevent="startTalking"
          @touchend.prevent="stopTalking"
        >
          🎤 Push to Talk
        </button>

        <p v-if="talking" class="text-green-600 font-semibold mt-2">
          🎙️ You are speaking
        </p>

        <div class="text-left mt-6">
          <h2 class="text-sm font-bold">Connected Users:</h2>
          <ul class="text-sm text-gray-700 mt-1">
            <li v-for="user in connectedUsers" :key="user">
              👤 {{ user === socket.id ? "You" : user }}
            </li>
          </ul>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { io } from "socket.io-client";

const apiUrl = import.meta.env.VITE_API_URL;
const socket = io(apiUrl, { secure: true });

const roomId = ref("");
const joined = ref(false);
const localStream = ref(null);
const talking = ref(false);
const connectedUsers = ref([]);

const peerConnections = {};
const remoteAudioElements = {};

const config = {
  iceServers: [{ urls: "stun:stun.l.google.com:19302" }],
};

function attachRemoteAudio(userId, track) {
  const stream = new MediaStream();
  stream.addTrack(track);
  const audio = document.createElement("audio");
  audio.srcObject = stream;
  audio.autoplay = true;
  document.body.appendChild(audio);
  remoteAudioElements[userId] = audio;
}

function removeRemoteAudio(userId) {
  if (remoteAudioElements[userId]) {
    remoteAudioElements[userId].remove();
    delete remoteAudioElements[userId];
  }
}

const joinRoom = async () => {
  try {
    localStream.value = await navigator.mediaDevices.getUserMedia({
      audio: true,
    });
    localStream.value
      .getAudioTracks()
      .forEach((track) => (track.enabled = false));
    socket.emit("join", roomId.value);
    joined.value = true;
  } catch (err) {
    console.log(err);
    alert("Failed to access microphone");
    console.error(err);
  }
};

const startTalking = () => {
  talking.value = true;
  localStream.value
    ?.getAudioTracks()
    .forEach((track) => (track.enabled = true));
};

const stopTalking = () => {
  talking.value = false;
  localStream.value
    ?.getAudioTracks()
    .forEach((track) => (track.enabled = false));
};

socket.on("user-list", (users) => {
  connectedUsers.value = users;
});

socket.on("user-joined", async (userId) => {
  const pc = new RTCPeerConnection(config);
  peerConnections[userId] = pc;

  localStream.value
    .getTracks()
    .forEach((track) => pc.addTrack(track, localStream.value));

  pc.onicecandidate = (e) => {
    if (e.candidate) {
      socket.emit("ice-candidate", { target: userId, candidate: e.candidate });
    }
  };

  pc.ontrack = (event) => {
    attachRemoteAudio(userId, event.track);
  };

  const offer = await pc.createOffer();
  await pc.setLocalDescription(offer);
  socket.emit("offer", { target: userId, offer });
});

socket.on("offer", async ({ from, offer }) => {
  const pc = new RTCPeerConnection(config);
  peerConnections[from] = pc;

  localStream.value
    .getTracks()
    .forEach((track) => pc.addTrack(track, localStream.value));

  pc.onicecandidate = (e) => {
    if (e.candidate) {
      socket.emit("ice-candidate", { target: from, candidate: e.candidate });
    }
  };

  pc.ontrack = (event) => {
    attachRemoteAudio(from, event.track);
  };

  await pc.setRemoteDescription(new RTCSessionDescription(offer));
  const answer = await pc.createAnswer();
  await pc.setLocalDescription(answer);
  socket.emit("answer", { target: from, answer });
});

socket.on("answer", async ({ from, answer }) => {
  const pc = peerConnections[from];
  if (pc) await pc.setRemoteDescription(new RTCSessionDescription(answer));
});

socket.on("ice-candidate", async ({ from, candidate }) => {
  const pc = peerConnections[from];
  if (pc && candidate) await pc.addIceCandidate(new RTCIceCandidate(candidate));
});

socket.on("user-left", (userId) => {
  const pc = peerConnections[userId];
  if (pc) {
    pc.close();
    delete peerConnections[userId];
    removeRemoteAudio(userId);
  }
});
</script>
