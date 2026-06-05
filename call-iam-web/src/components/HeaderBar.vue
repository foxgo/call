<template>
  <header class="header-bar">
    <div>
      <p class="header-bar__eyebrow">Console</p>
      <h2 class="header-bar__title">统一身份中心</h2>
    </div>
  </header>
</template>

<style scoped>
.header-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 24px 24px 0;
}

.header-bar__eyebrow {
    margin: 0 0 6px;
    font-size: 12px;
    letter-spacing: 0.22em;
    text-transform: uppercase;
    color: var(--iam-accent);
}

.header-bar__title {
    margin: 0;
    font-size: 28px;
}
</style>
