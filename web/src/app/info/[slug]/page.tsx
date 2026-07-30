import type { Metadata } from "next";
import { headers } from "next/headers";
import { notFound } from "next/navigation";
import { FAQ_ITEMS, buildFaqPageJsonLd } from "@/lib/csp-inline-scripts";
import { JsonLdBreadcrumb } from "@/components/json-ld";
import { getPublicBaseUrl } from "@/lib/api";
import {
  INFO_PAGE_METADATA,
  INFO_PAGE_SLUGS,
  isInfoPageSlug,
  type InfoPageSlug,
} from "@/lib/info-page-metadata";
import { serializeJsonLd } from "@/lib/json-ld-serialize";
import styles from "./info.module.css";

type Props = { params: Promise<{ slug: string }> };

type InfoPage = {
  content: React.ReactNode;
};

const infoPages: Record<InfoPageSlug, InfoPage> = {
  "data-source": {
    content: (
      <article className={styles.article}>
        <h2>공식 데이터 출처</h2>
        <p>
          KRAFT Lotto에 표시되는 로또 6/45 당첨 번호와 당첨 금액 정보는
          동행복권 공식 웹사이트(<strong>dhlottery.co.kr</strong>)에서 제공하는
          공개 API를 기준으로 수집합니다.
        </p>

        <h2>수집 방식</h2>
        <ul>
          <li>추첨 결과 공개 후 자동으로 최신 회차를 반영합니다.</li>
          <li>수집된 데이터는 내부 저장소에 보관하고, 안정적인 조회를 위해 캐시와 함께 제공합니다.</li>
          <li>제1회부터 최신 회차까지의 전체 이력을 관리합니다.</li>
        </ul>

        <h2>데이터 정확성</h2>
        <p>
          당첨 번호, 당첨 금액, 판매 금액 등 주요 수치는 동행복권 공식 발표를 기준으로 표시합니다.
          통계 화면에 보이는 수치 역시 과거 누적 데이터를 바탕으로 계산한 참고 정보이며,
          미래 결과를 예측하거나 보장하지 않습니다.
        </p>

        <h2>저작권</h2>
        <p>
          공개된 당첨 결과 데이터는 이용자가 쉽게 확인하고 비교할 수 있도록 재구성해 제공합니다.
          KRAFT Lotto는 원본 데이터의 공식성을 존중하며 출처를 명시합니다.
        </p>

      </article>
    ),
  },

  methodology: {
    content: (
      <article className={styles.article}>
        <h2>통계 분석 개요</h2>
        <p>
          KRAFT Lotto의 통계 기능은 제1회부터 최신 회차까지의 실제 당첨 번호를 기준으로
          단순 빈도와 분포를 계산합니다. 예측 모델이나 머신러닝을 사용하지 않으며,
          결과 해석은 참고용 정보 제공에 목적이 있습니다.
        </p>

        <h2>빈도 분석 (Frequency)</h2>
        <p>
          각 번호(1~45)가 전체 회차에서 본번호로 등장한 횟수를 집계합니다.
          자주 나온 번호와 적게 나온 번호를 함께 보여 주어 상대적인 분포를 빠르게 확인할 수 있습니다.
        </p>

        <h2>패턴 분석 (Pattern)</h2>
        <ul>
          <li><strong>홀짝 비율:</strong> 당첨 번호 6개 중 홀수 개수를 0~6으로 분류합니다.</li>
          <li><strong>고저 비율:</strong> 1~22를 저번호, 23~45를 고번호로 분류하여 고번호 개수를 집계합니다.</li>
          <li><strong>합계 구간:</strong> 6개 번호의 합계를 21~65, 66~110, 111~155, 156~200, 201~255 구간으로 나눕니다.</li>
        </ul>

        <h2>동반 분석 (Companion)</h2>
        <p>
          두 번호가 같은 회차의 본번호에 함께 포함된 횟수를 모든 조합(45C2 = 990쌍)에 대해 계산합니다.
          그중 함께 나온 빈도가 높은 조합을 상위 순서대로 보여 줍니다.
        </p>

        <h2>번호 추천</h2>
        <p>
          서버 측에서 1~45 사이 숫자 중 6개를 무작위로 중복 없이 추출합니다.
          역대 1등 당첨 조합은 결과에서 제외하며, 제외 번호를 입력하면 해당 숫자는 후보에서 제거합니다.
        </p>
        <p>
          <strong>공동 당첨 위험 완화 추천</strong>은 비인기 조합을 우선 선택합니다.
          같은 회차에 동일 조합을 구매한 공동 당첨자가 적을수록 개인 수령액이 높아지는 원리를 활용합니다.
          생일 번호(1~31) 편향, 라운드 번호(5·7 배수) 편향, 낮은 합계 편향을 역이용해
          후보 50개 중 비인기도 점수가 가장 높은 조합을 반환합니다.
        </p>

        <h2>중요 안내</h2>
        <p>
          로또는 매 회차가 독립적으로 추첨됩니다. 과거 통계는 참고 자료일 뿐이며,
          특정 번호 조합이 다른 조합보다 구조적으로 더 유리하다고 볼 수 없습니다.
        </p>
      </article>
    ),
  },

  faq: {
    content: (
      <article className={styles.article}>
        {FAQ_ITEMS.map((item) => (
          <div className={styles.faqItem} key={item.question}>
            <h3>{item.question}</h3>
            <p>{item.answer}</p>
          </div>
        ))}
      </article>
    ),
  },

  privacy: {
    content: (
      <article className={styles.article}>
        <p className="muted">시행일: 2026년 1월 1일</p>

        <h2>수집하는 정보</h2>
        <p>
          KRAFT Lotto의 당첨 조회·추천 기능은 회원가입 없이 이용할 수 있습니다. 저장함 기능을 사용할 때는
          브라우저에 익명 기기 토큰(UUID)이 생성되며, 이 토큰과 연결된 저장 번호와 추천 이력이 서버에 보관될 수 있습니다.
          커뮤니티 로그인 시에는 OAuth 제공자명과 제공자별 식별자, 닉네임, 프로필 이미지 URL, 로그인 세션을 처리합니다.
          이메일과 회원 이름은 서비스에서 수집·저장하지 않습니다.
        </p>

        <h2>정보 이용 목적</h2>
        <ul>
          <li>기기 토큰: 저장한 번호를 현재 브라우저와 연결하기 위한 익명 식별자</li>
          <li>커뮤니티 계정: 로그인, 작성자 표시, 신고·차단과 운영 조치를 위한 OAuth 식별자·닉네임·프로필 이미지</li>
          <li>커뮤니티 활동: 게시글·댓글, 반응, 신고, 차단 및 관련 운영 기록</li>
          <li>추천·저장 이력: 사용자가 생성하거나 저장한 번호를 표시·관리하고 계정 귀속을 처리하기 위한 정보</li>
          <li>서버 로그: 장애 대응과 보안 점검을 위해 IP 주소, 접근 시각 등을 단기 보관</li>
        </ul>

        <h2>제3자 제공</h2>
        <p>수집된 정보는 법령상 요구가 있는 경우를 제외하고 제3자에게 제공하거나 판매하지 않습니다.</p>

        <h2>제3자 광고 서비스</h2>
        <p>
          KRAFT Lotto는 배포 설정에 따라 카카오 애드핏(Kakao Adfit) 또는 Google AdSense 광고 서비스를 사용할 수 있습니다.
          광고 제공을 위해 해당 사업자의 스크립트가 로드되며, 각 사업자는 쿠키 또는 유사 기술을 사용해
          관심 기반 광고를 제공할 수 있습니다. 수집·이용되는 정보와 거부 방법은
          <a href="https://www.kakao.com/policy/privacy" target="_blank" rel="noopener noreferrer">
            카카오 개인정보처리방침
          </a>
          을 참고하세요. Google AdSense가 활성화된 배포에서는 Google의 개인정보처리방침도 함께 적용됩니다.
        </p>

        <h2>보관 기간</h2>
        <p>
          저장 번호와 추천 이력은 사용자가 삭제하거나 계정을 탈퇴할 때까지 유지됩니다. 게시글·댓글·신고·운영 기록의
          보관 기간과 탈퇴 후 처리 기준, 광고 관련 처리의 세부 사항은 법률 검토를 거쳐 별도 개정 고지로 확정합니다.
          서버 로그는 최대 30일 동안 보관한 뒤 삭제합니다.
        </p>

        <h2>이용자의 권리</h2>
        <p>
          저장한 번호와 추천 이력은 각 보관함에서 직접 삭제할 수 있습니다. 커뮤니티 계정은 로그인 후 계정 메뉴의
          &ldquo;탈퇴&rdquo; 버튼으로 직접 탈퇴할 수 있으며, 탈퇴 시 계정 정보는 삭제되고 기존에 작성한 게시글·댓글은
          작성자 표시가 익명으로 전환됩니다. 게시물 처리, 추가 열람·정정·삭제 요청은 문의하기 페이지를 통해
          접수할 수 있습니다.
        </p>

        <h2>문의</h2>
        <p>개인정보 처리와 관련한 문의는 문의하기 페이지를 통해 접수할 수 있습니다.</p>
      </article>
    ),
  },

  terms: {
    content: (
      <article className={styles.article}>
        <p className="muted">시행일: 2026년 1월 1일</p>

        <h2>제1조 목적</h2>
        <p>
          본 약관은 KRAFT Lotto(이하 &ldquo;서비스&rdquo;)가 제공하는 로또 조회, 통계, 번호 추천 기능의
          이용 조건과 책임 범위를 정하는 데 목적이 있습니다.
        </p>

        <h2>제2조 서비스 내용</h2>
        <p>
          서비스는 동행복권 공식 데이터를 기반으로 과거 당첨 결과 조회,
          통계 분석, 무작위 번호 추천 기능을 제공합니다.
          서비스의 어떤 기능도 당첨을 예측하거나 보장하지 않습니다.
        </p>

        <h2>제3조 이용자 의무</h2>
        <ul>
          <li>서비스를 과도하게 수집하거나 상업적 목적으로 무단 재사용하는 행위를 금지합니다.</li>
          <li>서비스의 안정적 운영을 방해하거나 보안을 침해하는 행위를 금지합니다.</li>
        </ul>

        <h2>제4조 면책 사항</h2>
        <p>
          서비스는 참고용 정보 제공을 목적으로 운영됩니다. 번호 추천, 통계 결과,
          데이터 지연 또는 일시적 중단으로 인해 발생한 구매 결정과 손실에 대해 책임을 지지 않습니다.
        </p>

        <h2>제5조 약관 변경</h2>
        <p>
          약관이 변경되는 경우 서비스 화면을 통해 사전에 안내합니다.
          변경 후에도 서비스를 계속 이용하면 변경된 내용에 동의한 것으로 봅니다.
        </p>
      </article>
    ),
  },

  contact: {
    content: (
      <article className={styles.article}>
        <h2>문의 방법</h2>
        <p>
          서비스 이용 중 발견한 오류, 데이터 수정 요청, 개선 제안, 개인정보 관련 문의는
          아래 이메일로 보내 주세요.
        </p>

        <div className={styles.contactBox}>
          <p>
            <strong>이메일:</strong>{" "}
            <a href="mailto:portuna85@gmail.com">portuna85@gmail.com</a>
          </p>
          <p className="muted">
            접수된 문의는 영업일 기준 2~3일 내 확인 후 답변드립니다.
          </p>
        </div>

        <h2>버그 신고 시 포함 사항</h2>
        <ul>
          <li>문제가 발생한 페이지 URL</li>
          <li>사용한 브라우저와 기기 종류</li>
          <li>오류 내용 및 재현 방법</li>
        </ul>

        <h2>데이터 오류 신고</h2>
        <p>
          특정 회차의 당첨 번호나 당첨 금액이 공식 발표와 다를 경우,
          회차 번호와 함께 알려 주시면 확인 후 반영하겠습니다.
        </p>
      </article>
    ),
  },

  "responsible-play": {
    content: (
      <article className={styles.article}>
        <div className={styles.noticeBox}>
          <strong>로또는 오락의 한 형태로 가볍게 즐기는 것이 바람직합니다.</strong>
          <p>당첨 기대를 과도하게 키우기보다 여유 자금 안에서만 이용해 주세요.</p>
        </div>

        <h2>건전한 이용 원칙</h2>
        <ul>
          <li>생활비나 대출금처럼 꼭 필요한 자금은 사용하지 마세요.</li>
          <li>구매 횟수와 예산 한도를 미리 정하고 그 범위 안에서만 이용하세요.</li>
          <li>당첨을 전제로 한 재정 계획이나 소비 계획은 세우지 마세요.</li>
          <li>손실을 만회하려고 추가 구매를 반복하지 마세요.</li>
        </ul>

        <h2>KRAFT Lotto의 역할</h2>
        <p>
          KRAFT Lotto는 과거 당첨 결과와 통계를 정리해 보여 주는 서비스입니다.
          미래 당첨 번호를 예측하거나 당첨 가능성을 높여 주지 않으며, 과거 결과는 참고 자료일 뿐입니다.
        </p>

        <h2>도움이 필요하다면</h2>
        <p>
          구매 통제가 어렵거나 도박 문제로 불편을 겪고 있다면 아래 기관의 도움을 받아 보세요.
        </p>
        <ul>
          <li>
            <strong>한국도박문제관리센터:</strong>{" "}
            <a href="tel:1336">☎ 1336</a> (24시간)
          </li>
          <li>
            <strong>도박문제 무료 상담 온라인:</strong>{" "}
            <a
              href="https://www.kcgp.or.kr"
              target="_blank"
              rel="noopener noreferrer"
            >
              www.kcgp.or.kr
            </a>
          </li>
        </ul>

      </article>
    ),
  },
};

export function generateStaticParams() {
  return INFO_PAGE_SLUGS.map((slug) => ({ slug }));
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  if (!isInfoPageSlug(slug)) return {};
  const info = INFO_PAGE_METADATA[slug];
  return {
    title: info.title,
    description: info.description,
    alternates: { canonical: `/info/${slug}` },
  };
}

export default async function InfoPage({ params }: Props) {
  const { slug } = await params;
  if (!isInfoPageSlug(slug)) notFound();
  const info = infoPages[slug];
  const metadata = INFO_PAGE_METADATA[slug];
  const nonce = (await headers()).get("x-nonce") ?? undefined;
  const baseUrl = getPublicBaseUrl();

  return (
    <section className="panel">
      <JsonLdBreadcrumb baseUrl={baseUrl} nonce={nonce} items={[{ name: metadata.title, item: `${baseUrl}/info/${slug}` }]} />
      <p className="eyebrow">서비스 안내</p>
      <h1 className="page-title">{metadata.title}</h1>
      <p className="page-subtitle">{metadata.description}</p>
      {slug === "faq" ? (
        <script
          type="application/ld+json"
          nonce={nonce}
          suppressHydrationWarning
          dangerouslySetInnerHTML={{ __html: serializeJsonLd(buildFaqPageJsonLd()) }}
        />
      ) : null}
      {info.content}
    </section>
  );
}
