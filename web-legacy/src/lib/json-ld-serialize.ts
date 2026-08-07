// JSON.stringify만으로 <script type="application/ld+json"> 안에 값을 넣으면
// 값에 "</script>"가 섞여 있을 때(예: 사용자 제어 문자열) 스크립트 태그가 조기
// 종료될 수 있다. "<"를 유니코드 이스케이프로 바꿔 파서가 태그로 해석하지 못하게 한다.
export function serializeJsonLd(value: unknown): string {
  return JSON.stringify(value).replace(/</g, "\\u003c");
}
