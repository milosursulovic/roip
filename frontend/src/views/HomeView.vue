<template>
  <div
    class="min-h-screen bg-gray-100 flex flex-col items-center justify-center"
  >
    <div class="bg-white shadow-xl rounded-xl p-6 w-full max-w-md">
      <h1 class="text-2xl font-bold text-center mb-4">📡 ROIP Push-to-Talk</h1>

      <div v-if="!joined" class="space-y-4">
        <input
          v-model="roomId"
          type="text"
          placeholder="Enter channel name"
          class="input"
        />
        <button class="btn w-full" @click="joinRoom">Join Channel</button>
      </div>

      <div v-else class="space-y-4 text-center">
        <p class="font-semibold text-lg">
          ✅ Connected to: <strong>{{ roomId }}</strong>
        </p>
        <button
          class="ptt-button"
          @mousedown="startTalking"
          @mouseup="stopTalking"
          @touchstart.prevent="startTalking"
          @touchend.prevent="stopTalking"
        >
          🎤 Push to Talk
        </button>
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
const peerConnections = {};

const joinRoom = async () => {
  try {
    localStream.value = await navigator.mediaDevices.getUserMedia({
      audio: true,
    });
    socket.emit("join", roomId.value);
    joined.value = true;
  } catch (err) {
    alert("Failed to access microphone");
    console.error(err);
  }
};

const startTalking = () => {
  localStream.value
    ?.getAudioTracks()
    .forEach((track) => (track.enabled = true));
};

const stopTalking = () => {
  localStream.value
    ?.getAudioTracks()
    .forEach((track) => (track.enabled = false));
};
</script>
