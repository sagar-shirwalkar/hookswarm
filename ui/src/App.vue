<script setup>
</script>

<template>
  <div class="app-container">
    <header>
      <div class="logo">HookSwarm</div>
      <nav>
        <router-link to="/">Dashboard</router-link>
      </nav>
    </header>
    <main>
      <router-view></router-view>
    </main>
  </div>
</template>

<style>
body {
  margin: 0;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  background-color: #f5f7fa;
  color: #2c3e50;
}

.app-container {
  min-height: 100vh;
}

header {
  background-color: #2c3e50;
  color: white;
  padding: 0 20px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.logo {
  font-size: 1.5em;
  font-weight: bold;
}

nav a {
  color: white;
  text-decoration: none;
  margin-left: 20px;
}

nav a:hover {
  text-decoration: underline;
}

main {
  padding: 20px;
}
</style>
