// 정적 sha256 해시 방식을 시도했으나 Next가 내부적으로 주입하는 RSC 하이드레이션 스크립트까지는
// 허용할 수 없어 전 페이지 하이드레이션이 CSP에 막히는 문제가 있었다(회귀 이력은 web/src/proxy.ts 참고).
// 그래서 이 스크립트는 layout.tsx에서 매 요청 발급되는 nonce로 허용한다.
export const THEME_INIT_SCRIPT =
  "try{var t=localStorage.getItem('kraft-theme');if(t==='dark'||(t!=='light'&&matchMedia('(prefers-color-scheme: dark)').matches)){document.documentElement.setAttribute('data-theme','dark');}}catch(e){}";

export const FAQ_ITEMS: { question: string; answer: string }[] = [
  {
    question: "추천 번호로 사면 당첨 확률이 높아지나요?",
    answer:
      "아닙니다. 무작위·균형 조합·공동 당첨 위험 완화 전략은 번호를 선택하는 방식만 다르며, 특정 조합의 1등 확률을 높이지 않습니다. 로또 6/45의 모든 유효 조합은 같은 1등 확률을 가집니다.",
  },
  {
    question: "보관함은 어디에 저장되나요?",
    answer:
      "보관함에 저장한 번호는 브라우저에 자동 생성되는 익명 기기 토큰과 연결되어 서버에 저장됩니다. 브라우저 데이터를 삭제하거나 다른 기기에서 접속하면 같은 목록을 바로 이어서 볼 수 없습니다.",
  },
  {
    question: "세후 예상 금액은 어떻게 계산하나요?",
    answer:
      "당첨금 200만원 이하는 비과세, 200만원 초과 3억원 이하 구간은 22%, 3억원 초과 구간은 33%의 세율을 구간별로 적용해 합산합니다.",
  },
  {
    question: "과거 1등 조합을 제외하나요?",
    answer:
      "네. 모든 추천 전략은 역대 1등 당첨 조합을 후보에서 제외합니다. 다만 이 제외 여부와 관계없이 모든 유효한 조합의 1등 당첨 확률은 동일하며, 제외한다고 해서 남은 조합의 당첨 확률이 더 높아지지 않습니다.",
  },
  {
    question: "추천 전략 3가지는 무엇이 다른가요?",
    answer:
      "무작위는 조건 없이 1~45에서 임의로 6개를 뽑습니다. 균형 조합은 홀짝·고저·합계·구간 분포가 고르게 맞는 조합을 우선 선택합니다. 공동 당첨 위험 완화는 생일 편향·라운드 번호 편향처럼 많은 사람이 함께 고르는 패턴을 피해, 당첨되더라도 상금을 나눠 받을 위험이 낮은 조합을 고릅니다. 세 전략 모두 1등 당첨 확률 자체를 높이지 않습니다.",
  },
  {
    question: "광고나 유료 기능이 있나요?",
    answer:
      "화면에 광고가 표시될 수 있으나, 번호를 판매하거나 결제가 필요한 유료 기능은 제공하지 않습니다. 자세한 내용은 운영자 소개 페이지를 확인해 주세요.",
  },
  {
    question: "당첨 번호가 얼마나 빨리 업데이트되나요?",
    answer:
      "추첨 결과 공개 후 자동 수집을 통해 최신 회차를 반영합니다. 일반적으로 수 분 내 반영되지만, 외부 데이터 상황에 따라 다소 지연될 수 있습니다.",
  },
  {
    question: "몇 회차부터 데이터가 있나요?",
    answer: "2002년 12월 7일 추첨한 제1회부터 최신 회차까지의 전체 이력을 제공합니다.",
  },
  {
    question: "앱이 있나요?",
    answer: "현재는 웹 서비스 중심으로 제공하고 있으며, 모바일 브라우저에서도 동일하게 사용할 수 있습니다.",
  },
  {
    question: "오류나 개선 요청은 어떻게 하나요?",
    answer: "문의하기 페이지를 통해 오류 제보와 개선 요청을 보내 주세요.",
  },
];

export function buildFaqPageJsonLd() {
  return {
    "@context": "https://schema.org",
    "@type": "FAQPage",
    mainEntity: FAQ_ITEMS.map((item) => ({
      "@type": "Question",
      name: item.question,
      acceptedAnswer: { "@type": "Answer", text: item.answer },
    })),
  };
}

export function buildWebsiteJsonLd(baseUrl: string) {
  return {
    "@context": "https://schema.org",
    "@graph": [
      {
        "@type": "WebSite",
        "@id": `${baseUrl}/#website`,
        url: baseUrl,
        name: "KRAFT Lotto",
        description: "로또 당첨 결과 조회, 추천 조합 생성, 보관함 관리를 제공하는 서비스입니다.",
        inLanguage: "ko-KR",
      },
      {
        "@type": "Organization",
        "@id": `${baseUrl}/#organization`,
        url: baseUrl,
        name: "KRAFT Lotto",
      },
    ],
  };
}
