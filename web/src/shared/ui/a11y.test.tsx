import { render } from "@testing-library/react";
import axe from "axe-core";
import type { ComponentProps } from "react";
import { describe, expect, it, vi } from "vitest";

import { Badge, StatusBadge } from "./badge";
import { Button, IconButton } from "./button";
import { ConfirmDialog, Dialog } from "./dialog";
import { Checkbox, Radio, Select, TextArea, TextField } from "./field";
import { SegmentedControl } from "./segmented-control";
import { Card, Pagination, Table } from "./surface";
import { EmptyState, ErrorState, InlineAlert } from "./states";

// RSP-34(docs/improvement.md): Pagination이 렌더하는 실제 next/link의 내부
// 이펙트(prefetch 관련)가 render()의 act 경계 밖에서 상태를 갱신해 "not wrapped
// in act(...)" 경고가 났다. axe 검사는 실제 라우팅·prefetch 동작이 필요 없으므로
// navigation.test.tsx와 같은 형태로 원인 자체를 제거한다 — 계측이 아니라 실제
// Link를 평범한 <a>로 바꿔 이펙트를 없앤다(태그·role은 동일해 axe 결과에
// 영향이 없다).
vi.mock("next/link", () => ({
  default: ({
    href,
    prefetch,
    children,
    ...rest
  }: ComponentProps<"a"> & { prefetch?: boolean }) => (
    <a
      href={typeof href === "string" ? href : undefined}
      data-prefetch={String(prefetch)}
      {...rest}
    >
      {children}
    </a>
  ),
}));

/**
 * 프리미티브 axe 검사 Phase 3 검증 조건("프리미티브 axe 위반 0").
 *
 * 화면 단위 axe는 E2E가 담당하지만, 프리미티브 단계에서 잡아야 하는 위반이 따로 있다:
 * 라벨 없는 컨트롤, 이름 없는 다이얼로그, role 없이 흉내낸 위젯. 화면이 다 만들어진 뒤
 * 발견하면 수정 범위가 화면 전체로 번진다.
 */
async function violationsOf(ui: React.ReactElement): Promise<axe.Result[]> {
  const { container } = render(ui);
  const results = await axe.run(container, {
    // 색 대비는 jsdom이 실제 렌더 색을 계산하지 못해 신뢰할 수 없다 — E2E(실제 브라우저)가 본다.
    rules: { "color-contrast": { enabled: false } },
  });
  return results.violations;
}

describe("프리미티브 접근성", () => {
  it("Button·IconButton에 위반이 없다", async () => {
    expect(
      await violationsOf(
        <div>
          <Button>저장</Button>
          <Button loading loadingLabel="저장 중">
            저장
          </Button>
          <IconButton aria-label="닫기" icon={<span>✕</span>} />
        </div>,
      ),
    ).toEqual([]);
  });

  it("폼 필드에 위반이 없다", async () => {
    expect(
      await violationsOf(
        <form>
          <TextField label="제목" />
          <TextField label="검색어" hint="2자 이상" error="너무 짧습니다." />
          <TextArea label="본문" />
          <Select label="카테고리">
            <option value="GENERAL">자유</option>
          </Select>
          <Checkbox label="동의합니다" />
          <Radio name="pick" label="첫 번째" />
        </form>,
      ),
    ).toEqual([]);
  });

  it("상태 표시 컴포넌트에 위반이 없다", async () => {
    expect(
      await violationsOf(
        <div>
          <Badge tone="warning" label="점검 중" />
          <StatusBadge status="fresh" label="최신" />
          <InlineAlert tone="danger">삭제하지 못했습니다.</InlineAlert>
          <EmptyState reason="filtered" title="결과 없음" description="검색어를 바꿔 보세요." />
          <ErrorState
            title="불러오지 못했습니다"
            description="잠시 후 다시 시도해 주세요."
            action={<Button>다시 시도</Button>}
          />
        </div>,
      ),
    ).toEqual([]);
  });

  it("오버레이에 위반이 없다", async () => {
    expect(
      await violationsOf(
        <Dialog open onClose={() => {}} title="번호 삭제">
          <p>정말 삭제할까요?</p>
        </Dialog>,
      ),
    ).toEqual([]);

    expect(
      await violationsOf(
        <ConfirmDialog
          open
          onClose={() => {}}
          title="삭제할까요?"
          description="되돌릴 수 없습니다."
          confirmLabel="삭제"
          onConfirm={() => {}}
        />,
      ),
    ).toEqual([]);
  });

  it("선택 위젯에 위반이 없다", async () => {
    expect(
      await violationsOf(
        <SegmentedControl
          aria-label="정렬"
          value="latest"
          onChange={() => {}}
          options={[
            { value: "latest", label: "최신순" },
            { value: "weekly_popular", label: "주간 인기" },
          ]}
        />,
      ),
    ).toEqual([]);
  });

  it("표와 페이지네이션에 위반이 없다", async () => {
    expect(
      await violationsOf(
        <div>
          <Table caption="등수별 당첨금">
            <thead>
              <tr>
                <th scope="col">등수</th>
                <th scope="col">당첨금</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>1등</td>
                <td>20억</td>
              </tr>
            </tbody>
          </Table>
          <Pagination page={0} totalPages={5} buildHref={(page) => `?page=${page}`} />
          <Card>
            <p>카드 내용</p>
          </Card>
        </div>,
      ),
    ).toEqual([]);
  });
});
