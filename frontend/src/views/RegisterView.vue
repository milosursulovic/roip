<template>
  <div class="flex justify-center items-center min-h-screen bg-gray-100">
    <div class="bg-white p-8 rounded-xl shadow-lg w-full max-w-sm">
      <h1 class="text-xl font-bold mb-4">📝 Register</h1>
      <form @submit.prevent="handleRegister">
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
        <input
          v-model="confirmPassword"
          type="password"
          placeholder="Confirm Password"
          class="input mt-2"
        />
        <button class="btn mt-4 w-full">Register</button>
      </form>
      <p class="text-sm text-gray-600 mt-4">
        Already have an account?
        <router-link to="/login" class="text-blue-600 hover:underline"
          >Login</router-link
        >
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";

const username = ref("");
const password = ref("");
const confirmPassword = ref("");
const router = useRouter();
const apiUrl = import.meta.env.VITE_API_URL;

const handleRegister = async () => {
  if (password.value !== confirmPassword.value) {
    alert("Passwords do not match");
    return;
  }

  const res = await fetch(`${apiUrl}/api/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      username: username.value,
      password: password.value,
    }),
  });

  if (res.ok) {
    alert("Registration successful");
    router.push("/login");
  } else {
    const data = await res.json();
    alert(data.error || "Registration failed");
  }
};
</script>