import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { TextField } from "@/ui/primitives/text-field";

describe("TextField 프리미티브", () => {
  it("label과 input이 htmlFor/id로 연결된다", () => {
    render(<TextField id="nickname" label="닉네임" value="" onChange={() => {}} />);
    expect(screen.getByLabelText("닉네임")).toBeInTheDocument();
  });

  it("입력 시 onChange에 새 값을 전달한다", () => {
    const onChange = vi.fn();
    render(<TextField id="nickname" label="닉네임" value="" onChange={onChange} />);
    fireEvent.change(screen.getByLabelText("닉네임"), { target: { value: "kraft" } });
    expect(onChange).toHaveBeenCalledWith("kraft");
  });

  it("invalid=true면 aria-invalid와 errorMessageId를 연결한다", () => {
    render(
      <TextField
        id="nickname"
        label="닉네임"
        value=""
        onChange={() => {}}
        invalid
        errorMessageId="nickname-error"
      />
    );
    const input = screen.getByLabelText("닉네임");
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(input).toHaveAttribute("aria-describedby", "nickname-error");
  });

  it("평상시 안내와 오류 메시지를 함께 설명으로 연결한다", () => {
    render(
      <TextField
        id="nickname"
        label="닉네임"
        value=""
        onChange={() => {}}
        descriptionId="nickname-hint"
        invalid
        errorMessageId="nickname-error"
      />
    );
    expect(screen.getByLabelText("닉네임")).toHaveAttribute(
      "aria-describedby",
      "nickname-hint nickname-error"
    );
  });
});
