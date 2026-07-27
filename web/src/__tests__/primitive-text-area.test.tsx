import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { TextArea } from "@/ui/primitives/text-area";

describe("TextArea 프리미티브", () => {
  it("label과 textarea가 htmlFor/id로 연결된다", () => {
    render(<TextArea id="comment" label="댓글" value="" onChange={() => {}} />);
    expect(screen.getByLabelText("댓글")).toBeInTheDocument();
  });

  it("입력 시 onChange에 새 값을 전달한다", () => {
    const onChange = vi.fn();
    render(<TextArea id="comment" label="댓글" value="" onChange={onChange} />);
    fireEvent.change(screen.getByLabelText("댓글"), { target: { value: "좋은 글이네요" } });
    expect(onChange).toHaveBeenCalledWith("좋은 글이네요");
  });

  it("invalid=true면 aria-invalid와 errorMessageId를 연결한다", () => {
    render(
      <TextArea
        id="comment"
        label="댓글"
        value=""
        onChange={() => {}}
        invalid
        errorMessageId="comment-error"
      />
    );
    const textarea = screen.getByLabelText("댓글");
    expect(textarea).toHaveAttribute("aria-invalid", "true");
    expect(textarea).toHaveAttribute("aria-describedby", "comment-error");
  });
});
