# syntax=docker/dockerfile:1.7
FROM node:24-bookworm-slim@sha256:2c87ef9bd3c6a3bd4b472b4bec2ce9d16354b0c574f736c476489d09f560a203 AS deps
WORKDIR /app
COPY package.json package-lock.json .npmrc ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --no-audit --no-fund

FROM node:24-bookworm-slim@sha256:2c87ef9bd3c6a3bd4b472b4bec2ce9d16354b0c574f736c476489d09f560a203 AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# robots.txt/sitemap.xml/metadataBase는 정적 생성이라 빌드 시점 값이 산출물에 박힌다.
# 런타임 환경변수(docker-compose)만으로는 반영되지 않으므로 빌드 인자로 받는다.
ARG KRAFT_PUBLIC_BASE_URL
ARG NEXT_PUBLIC_NAVER_SITE_VERIFICATION
ARG NEXT_PUBLIC_GOOGLE_SITE_VERIFICATION
ARG NEXT_PUBLIC_KAKAO_ADFIT_UNIT_FREQUENCY
ARG NEXT_PUBLIC_KAKAO_ADFIT_UNIT_FREQUENCY_DESKTOP
ARG NEXT_PUBLIC_KAKAO_ADFIT_UNIT_STICKY
# 슬롯당 애드핏/애드센스 중 하나만 렌더 — 기본값 adfit(애드센스 승인 전 운영 상태 유지).
ARG NEXT_PUBLIC_AD_NETWORK=adfit
ARG NEXT_PUBLIC_ADSENSE_CLIENT_ID
ARG NEXT_PUBLIC_ADSENSE_UNIT_SIDEBAR
ARG NEXT_PUBLIC_ADSENSE_UNIT_FREQUENCY
ARG NEXT_PUBLIC_ADSENSE_UNIT_FREQUENCY_MOBILE
# TD-011: 로컬에서 CLS 없이 레이아웃을 미리 검증할 때만 쓰는 옵트인 플래그(기본 off) —
# 다른 NEXT_PUBLIC_ADSENSE_* 빌드 인자와 동일하게 다룬다.
ARG NEXT_PUBLIC_ADSENSE_RESERVE_PLACEHOLDER
ENV KRAFT_PUBLIC_BASE_URL=$KRAFT_PUBLIC_BASE_URL
ENV NEXT_PUBLIC_NAVER_SITE_VERIFICATION=$NEXT_PUBLIC_NAVER_SITE_VERIFICATION
ENV NEXT_PUBLIC_GOOGLE_SITE_VERIFICATION=$NEXT_PUBLIC_GOOGLE_SITE_VERIFICATION
ENV NEXT_PUBLIC_KAKAO_ADFIT_UNIT_FREQUENCY=$NEXT_PUBLIC_KAKAO_ADFIT_UNIT_FREQUENCY
ENV NEXT_PUBLIC_KAKAO_ADFIT_UNIT_FREQUENCY_DESKTOP=$NEXT_PUBLIC_KAKAO_ADFIT_UNIT_FREQUENCY_DESKTOP
ENV NEXT_PUBLIC_KAKAO_ADFIT_UNIT_STICKY=$NEXT_PUBLIC_KAKAO_ADFIT_UNIT_STICKY
ENV NEXT_PUBLIC_AD_NETWORK=$NEXT_PUBLIC_AD_NETWORK
ENV NEXT_PUBLIC_ADSENSE_CLIENT_ID=$NEXT_PUBLIC_ADSENSE_CLIENT_ID
ENV NEXT_PUBLIC_ADSENSE_UNIT_SIDEBAR=$NEXT_PUBLIC_ADSENSE_UNIT_SIDEBAR
ENV NEXT_PUBLIC_ADSENSE_UNIT_FREQUENCY=$NEXT_PUBLIC_ADSENSE_UNIT_FREQUENCY
ENV NEXT_PUBLIC_ADSENSE_UNIT_FREQUENCY_MOBILE=$NEXT_PUBLIC_ADSENSE_UNIT_FREQUENCY_MOBILE
ENV NEXT_PUBLIC_ADSENSE_RESERVE_PLACEHOLDER=$NEXT_PUBLIC_ADSENSE_RESERVE_PLACEHOLDER
RUN npm run build

FROM node:24-bookworm-slim@sha256:2c87ef9bd3c6a3bd4b472b4bec2ce9d16354b0c574f736c476489d09f560a203 AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3000
ENV HOSTNAME=0.0.0.0
# 런타임은 `node server.js`만 실행하므로 npm/npx는 불필요하다. 베이스 이미지에
# 전역 설치된 npm이 자체 번들 undici를 끌고 다녀 이미지 취약점 스캔을 오염시키므로,
# 사용하지 않는 npm CLI 자체를 제거해 둔다.
RUN rm -rf /usr/local/lib/node_modules/npm /usr/local/bin/npm /usr/local/bin/npx /usr/local/bin/corepack \
    && useradd --create-home --uid 10001 nextjs \
    && mkdir -p /app/.next/cache \
    && chown -R nextjs:nextjs /app/.next/cache
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static
COPY --from=builder /app/public ./public
COPY healthcheck.js ./healthcheck.js
USER 10001:10001
EXPOSE 3000
HEALTHCHECK --interval=15s --timeout=5s --retries=10 CMD node healthcheck.js
CMD ["node", "server.js"]
