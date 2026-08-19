import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import * as v from "valibot";
import { describe, expect, it, vi } from "vitest";

import { ApiError } from "@/shared/api/error";

import { useForm } from "./use-form";

const schema = v.object({
  title: v.pipe(v.string(), v.minLength(1, "제목을 입력해 주세요.")),
  body: v.pipe(v.string(), v.minLength(5, "본문은 5자 이상이어야 합니다.")),
});

type Values = v.InferOutput<typeof schema>;

function PostForm({
  onSubmit,
  mapServerError,
}: {
  onSubmit: (values: Values) => Promise<void>;
  mapServerError?: (error: ApiError) => { field?: keyof Values; message: string };
}) {
  const { state, field, handleSubmit } = useForm<Values>({
    initialValues: { title: "", body: "" },
    schema,
    onSubmit,
    ...(mapServerError === undefined ? {} : { mapServerError }),
  });

  const title = field("title");
  const body = field("body");

  return (
    <form onSubmit={handleSubmit}>
      <label htmlFor="title">제목</label>
      <input
        id="title"
        name="title"
        value={title.value}
        onChange={(event) => title.onChange(event.target.value)}
        onBlur={title.onBlur}
      />
      {title.error !== null && <p role="alert">{title.error}</p>}

      <label htmlFor="body">본문</label>
      <input
        id="body"
        name="body"
        value={body.value}
        onChange={(event) => body.onChange(event.target.value)}
        onBlur={body.onBlur}
      />
      {body.error !== null && <p role="alert">{body.error}</p>}

      {state.formError !== null && <p role="alert">{state.formError}</p>}
      <button type="submit" disabled={state.isSubmitting}>
        {state.isSubmitting ? "제출 중" : "제출"}
      </button>
      <p>dirty: {String(state.isDirty)}</p>
    </form>
  );
}

describe("useForm", () => {
  it("입력 중에는 검증하지 않는다", async () => {
    render(<PostForm onSubmit={vi.fn().mockResolvedValue(undefined)} />);

    await userEvent.type(screen.getByLabelText("본문"), "짧");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("blur 시 해당 필드를 검증한다", async () => {
    render(<PostForm onSubmit={vi.fn().mockResolvedValue(undefined)} />);

    await userEvent.type(screen.getByLabelText("본문"), "짧");
    await userEvent.tab();

    expect(screen.getByRole("alert")).toHaveTextContent("본문은 5자 이상이어야 합니다.");
  });

  it("검증에 실패하면 제출 콜백을 부르지 않는다", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<PostForm onSubmit={onSubmit} />);

    await userEvent.click(screen.getByRole("button", { name: "제출" }));

    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getAllByRole("alert").length).toBeGreaterThan(0);
  });

  it("제출 실패 시 첫 오류 필드로 포커스를 옮긴다", async () => {
    render(<PostForm onSubmit={vi.fn().mockResolvedValue(undefined)} />);

    await userEvent.click(screen.getByRole("button", { name: "제출" }));

    expect(document.activeElement).toBe(screen.getByLabelText("제목"));
  });

  it("유효하면 값을 그대로 넘겨 제출한다", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<PostForm onSubmit={onSubmit} />);

    await userEvent.type(screen.getByLabelText("제목"), "안녕");
    await userEvent.type(screen.getByLabelText("본문"), "충분히 긴 본문");
    await userEvent.click(screen.getByRole("button", { name: "제출" }));

    expect(onSubmit).toHaveBeenCalledWith({ title: "안녕", body: "충분히 긴 본문" });
  });

  it("제출 중에는 버튼을 잠가 중복 제출을 막는다", async () => {
    let release: (() => void) | undefined;
    const onSubmit = vi.fn().mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          release = resolve;
        }),
    );
    render(<PostForm onSubmit={onSubmit} />);

    await userEvent.type(screen.getByLabelText("제목"), "안녕");
    await userEvent.type(screen.getByLabelText("본문"), "충분히 긴 본문");
    await userEvent.click(screen.getByRole("button", { name: "제출" }));

    expect(await screen.findByRole("button", { name: "제출 중" })).toBeDisabled();
    expect(onSubmit).toHaveBeenCalledTimes(1);
    release?.();
  });

  it("서버 오류를 해당 필드로 되돌리고 그 필드로 포커스를 옮긴다", async () => {
    // KF-23(docs/improvement.md): 계약 주석("첫 오류 필드로 포커스를 옮긴다")이
    // 로컬 검증에만 지켜지고 서버 매핑 오류는 빠져 있었다.
    const onSubmit = vi.fn().mockRejectedValue(new ApiError("client", "중복", { status: 409 }));
    render(
      <PostForm
        onSubmit={onSubmit}
        mapServerError={() => ({ field: "title", message: "이미 같은 제목의 글이 있습니다." })}
      />,
    );

    await userEvent.type(screen.getByLabelText("제목"), "안녕");
    await userEvent.type(screen.getByLabelText("본문"), "충분히 긴 본문");
    await userEvent.click(screen.getByRole("button", { name: "제출" }));

    expect(await screen.findByText("이미 같은 제목의 글이 있습니다.")).toBeInTheDocument();
    expect(screen.getByLabelText("제목")).toHaveFocus();
  });

  it("필드로 매핑할 수 없는 서버 오류는 폼 오류로 남긴다", async () => {
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError("server", "잠시 후 다시 시도해 주세요."));
    render(<PostForm onSubmit={onSubmit} />);

    await userEvent.type(screen.getByLabelText("제목"), "안녕");
    await userEvent.type(screen.getByLabelText("본문"), "충분히 긴 본문");
    await userEvent.click(screen.getByRole("button", { name: "제출" }));

    expect(await screen.findByText("잠시 후 다시 시도해 주세요.")).toBeInTheDocument();
  });

  it("값이 바뀌면 dirty가 된다 — 작성 중 이탈 경고의 근거", async () => {
    render(<PostForm onSubmit={vi.fn().mockResolvedValue(undefined)} />);

    expect(screen.getByText("dirty: false")).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("제목"), "안");
    expect(screen.getByText("dirty: true")).toBeInTheDocument();
  });
});
