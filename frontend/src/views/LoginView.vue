<template>
  <div class="flex justify-center items-center min-h-screen bg-gray-100">
    <div class="bg-white p-8 rounded-xl shadow-lg w-full max-w-sm">
      <h1 class="text-xl font-bold mb-4">🔐 Login</h1>
      <form @submit.prevent="handleLogin">
        <input
          v-model="username"
          type="text"
          placeholder="Username"
          class="input"
        />
        <input
          v-model="password"
          type="password"
          placeholder="Password"
          class="input mt-2"
        />
        <button class="btn mt-4 w-full">Login</button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";

const username = ref("");
const password = ref("");
const router = useRouter();
const apiUrl = import.meta.env.VITE_API_URL;

const handleLogin = async () => {
  const res = await fetch(`${apiUrl}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      username: username.value,
      password: password.value,
    }),
  });

  if (res.ok) {
    const data = await res.json();
    localStorage.setItem("token", data.token);
    router.push("/");
  } else {
    alert("Login failed");
  }
};
</script>