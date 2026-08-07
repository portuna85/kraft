import nextConfig from "eslint-config-next";

// Phase 1에서 4계층 의존 규칙(no-restricted-imports)이 여기에 추가된다 —
// improvement_fe.md §7.3. 규칙을 도구로 강제하지 않으면 계층이 조용히 무너지므로
// 코드보다 규칙이 먼저다(§27.1).
const config = [{ ignores: [".next/**", ".next-*/**", "next-env.d.ts"] }, ...nextConfig];

export default config;
