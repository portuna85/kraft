import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Checkbox, Select, TextArea, TextField } from "./field";

describe("TextField", () => {
  it("라벨을 컨트롤에 연결한다", () => {
    render(<TextField label="제목" />);
    expect(screen.getByLabelText("제목")).toBeInTheDocument();
  });

  it("오류가 있으면 aria-invalid와 함께 오류 메시지를 연결한다", () => {
    render(<TextField label="제목" error="제목을 입력해 주세요." />);

    const input = screen.getByLabelText("제목");
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(input).toHaveAccessibleDescription(/제목을 입력해 주세요/);
  });

  it("오류가 없으면 aria-invalid를 붙이지 않는다", () => {
    render(<TextField label="제목" />);
    expect(screen.getByLabelText("제목")).not.toHaveAttribute("aria-invalid");
  });

  it("힌트도 접근 설명으로 연결한다", () => {
    render(<TextField label="검색어" hint="2자 이상 입력하세요." />);
    expect(screen.getByLabelText("검색어")).toHaveAccessibleDescription(/2자 이상/);
  });

  it("오류 메시지는 등장 즉시 읽히도록 alert 역할을 갖는다", () => {
    render(<TextField label="제목" error="필수 항목입니다." />);
    expect(screen.getByRole("alert")).toHaveTextContent("필수 항목입니다.");
  });
});

describe("TextArea", () => {
  it("라벨과 오류를 동일한 규칙으로 연결한다", () => {
    render(<TextArea label="본문" error="본문을 입력해 주세요." />);

    const textarea = screen.getByLabelText("본문");
    expect(textarea).toHaveAttribute("aria-invalid", "true");
    expect(textarea).toHaveAccessibleDescription(/본문을 입력해 주세요/);
  });
});

describe("Select", () => {
  it("네이티브 select에 라벨을 연결한다", () => {
    render(
      <Select label="카테고리">
        <option value="GENERAL">자유</option>
      </Select>,
    );
    expect(screen.getByLabelText("카테고리")).toBeInTheDocument();
  });
});

describe("Checkbox", () => {
  it("라벨 텍스트로 찾을 수 있다", () => {
    render(<Checkbox label="공지에 동의합니다" />);
    expect(screen.getByRole("checkbox", { name: "공지에 동의합니다" })).toBeInTheDocument();
  });
});
