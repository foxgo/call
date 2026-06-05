<template>
  <section class="page-shell">
    <header class="page-header">
      <div>
        <p class="page-eyebrow">Department</p>
        <h1>部门管理</h1>
      </div>
    </header>

    <section class="tree-card">
      <ul class="tree-list">
        <li>
          <span>平台运营中心</span>
          <ul>
            <li>华东客服一部</li>
            <li>质检支持组</li>
          </ul>
        </li>
      </ul>
    </section>
  </section>
</template>

<style scoped>
.page-shell {
    display: grid;
    gap: 20px;
}

.page-eyebrow {
    margin: 0 0 8px;
    font-size: 12px;
    letter-spacing: 0.22em;
    text-transform: uppercase;
    color: var(--iam-accent);
}

h1 {
    margin: 0;
}

.tree-card {
    padding: 24px;
    border-radius: 20px;
    background: var(--iam-surface);
    border: 1px solid var(--iam-border);
}

.tree-list,
.tree-list ul {
    margin: 0;
    padding-left: 20px;
}

.tree-list li {
    margin: 12px 0;
}
</style>
