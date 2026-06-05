<template>
  <section class="page-shell">
    <header class="page-header">
      <div>
        <p class="page-eyebrow">Audit</p>
        <h1>审计日志</h1>
      </div>
    </header>

    <section class="filter-card">
      <input type="text" placeholder="按操作者搜索" />
      <select>
        <option>全部动作</option>
        <option>LOGIN</option>
        <option>CREATE_USER</option>
      </select>
    </section>

    <section class="table-card">
      <article class="table-row">
        <div>
          <h2>Tenant Admin</h2>
          <p>CREATE_USER / 2026-06-05 09:00</p>
        </div>
        <strong>成功</strong>
      </article>
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

h1,
h2,
p {
    margin: 0;
}

.filter-card,
.table-row {
    display: flex;
    align-items: center;
    gap: 16px;
}

.filter-card input,
.filter-card select {
    padding: 10px 14px;
    border-radius: 12px;
    border: 1px solid var(--iam-border);
}

.table-row {
    justify-content: space-between;
    padding: 18px 20px;
    border-radius: 20px;
    background: var(--iam-surface);
    border: 1px solid var(--iam-border);
}
</style>
