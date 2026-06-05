<template>
  <main class="login-view">
    <section class="login-card">
      <p class="login-eyebrow">Call IAM</p>
      <h1>登录</h1>
      <p class="login-copy">统一身份中心控制台</p>
    </section>
  </main>
</template>

<style scoped>
.login-view {
    min-height: 100vh;
    display: grid;
    place-items: center;
    padding: 24px;
}

.login-card {
    width: min(420px, 100%);
    padding: 40px 32px;
    border: 1px solid var(--iam-border);
    border-radius: 28px;
    background: var(--iam-surface);
    box-shadow: 0 24px 80px rgba(15, 23, 42, 0.14);
}

.login-eyebrow {
    margin: 0 0 12px;
    font-size: 12px;
    letter-spacing: 0.24em;
    text-transform: uppercase;
    color: var(--iam-accent);
}

h1 {
    margin: 0;
    font-size: 40px;
    line-height: 1.1;
}

.login-copy {
    margin: 12px 0 0;
    color: var(--iam-muted);
}
</style>
