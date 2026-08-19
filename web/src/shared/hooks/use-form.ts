"use client";

import { useCallback, useMemo, useRef, useState, type FormEvent } from "react";
import * as v from "valibot";

import type { ApiError } from "@/shared/api/error";

/**
 * 폼 단일 계약
 *
 * 현행은 폼 3개가 각자 useState로 값·오류·제출중·dirty를 재발명해, 중복 제출 방지와
 * 서버 검증 오류 표시가 화면마다 다르게 구현되거나 아예 빠져 있었다(§6.3 M-2). 폼이
 * 4개뿐이라 React Hook Form의 9KB는 과하고, 필요한 것은 아래 계약뿐이다(§8.4).
 *
 * 규칙(§16.2)
 * - 검증은 **blur와 제출 시**에만. 타이핑 중 오류가 깜빡이면 방해가 된다.
 * - 제출 중에는 재제출을 막는다.
 * - 제출 실패 시 첫 오류 필드로 포커스를 옮긴다 — 어디가 틀렸는지 화면 밖일 수 있다.
 * - 클라이언트 검증은 UX용이고 최종 판정은 백엔드다. 그래서 서버 오류를 필드로
 *   되돌리는 경로(mapServerError)가 계약에 포함된다.
 */

export type FormState<T> = {
  values: T;
  errors: Partial<Record<keyof T, string>>;
  formError: string | null;
  touched: Partial<Record<keyof T, boolean>>;
  isDirty: boolean;
  isSubmitting: boolean;
  submitCount: number;
};

export type FieldProps<T, K extends keyof T> = {
  name: K;
  value: T[K];
  onChange: (value: T[K]) => void;
  onBlur: () => void;
  error: string | null;
};

type Schema<T> = v.BaseSchema<unknown, T, v.BaseIssue<unknown>>;

export type UseFormConfig<T extends Record<string, unknown>> = {
  initialValues: T;
  schema: Schema<T>;
  onSubmit: (values: T) => Promise<void>;
  /** 백엔드 오류를 필드 또는 폼 오류로 되돌린다. 매핑 불가면 폼 오류가 된다. */
  mapServerError?: (error: ApiError) => { field?: keyof T; message: string };
};

function collectIssues<T>(schema: Schema<T>, values: unknown): Partial<Record<keyof T, string>> {
  const result = v.safeParse(schema, values);
  if (result.success) return {};

  const errors: Partial<Record<keyof T, string>> = {};
  for (const issue of result.issues) {
    const key = issue.path?.[0]?.key as keyof T | undefined;
    // 같은 필드에 여러 오류가 있으면 첫 번째만 보여준다 — 한 번에 하나씩 고치게 한다.
    if (key !== undefined && errors[key] === undefined) {
      errors[key] = issue.message;
    }
  }
  return errors;
}

export function useForm<T extends Record<string, unknown>>({
  initialValues,
  schema,
  onSubmit,
  mapServerError,
}: UseFormConfig<T>) {
  const [values, setValues] = useState<T>(initialValues);
  const [errors, setErrors] = useState<Partial<Record<keyof T, string>>>({});
  const [touched, setTouched] = useState<Partial<Record<keyof T, boolean>>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitCount, setSubmitCount] = useState(0);

  // 초기값은 렌더 중에 읽히므로(dirty 판정) ref가 아니라 state여야 한다 —
  // ref로 두면 reset 이후 dirty 표시가 갱신되지 않는다.
  const [baseline, setBaseline] = useState<T>(initialValues);
  const formRef = useRef<HTMLFormElement | null>(null);

  const isDirty = useMemo(
    () => Object.keys(values).some((key) => values[key] !== baseline[key]),
    [values, baseline],
  );

  const setValue = useCallback(<K extends keyof T>(name: K, value: T[K]) => {
    setValues((current) => ({ ...current, [name]: value }));
    // 입력 중에는 새 오류를 만들지 않되, 이미 떠 있던 오류는 지워 준다.
    setErrors((current) => ({ ...current, [name]: undefined }));
  }, []);

  const validateField = useCallback(
    <K extends keyof T>(name: K) => {
      setTouched((current) => ({ ...current, [name]: true }));
      setErrors((current) => ({ ...current, [name]: collectIssues(schema, values)[name] }));
    },
    [schema, values],
  );

  const field = useCallback(
    <K extends keyof T>(name: K): FieldProps<T, K> => ({
      name,
      value: values[name],
      onChange: (value: T[K]) => setValue(name, value),
      onBlur: () => validateField(name),
      error: touched[name] === true ? (errors[name] ?? null) : null,
    }),
    [values, errors, touched, setValue, validateField],
  );

  const handleSubmit = useCallback(
    (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      if (isSubmitting) return;

      formRef.current = event.currentTarget;
      setSubmitCount((count) => count + 1);
      setFormError(null);

      const nextErrors = collectIssues(schema, values);
      const firstInvalid = Object.keys(nextErrors)[0];

      if (firstInvalid !== undefined) {
        setErrors(nextErrors);
        setTouched(
          Object.fromEntries(Object.keys(values).map((key) => [key, true])) as Partial<
            Record<keyof T, boolean>
          >,
        );
        formRef.current?.querySelector<HTMLElement>(`[name="${firstInvalid}"]`)?.focus();
        return;
      }

      setErrors({});
      setIsSubmitting(true);

      void onSubmit(values)
        .catch((cause: unknown) => {
          const mapped = mapServerError?.(cause as ApiError);
          if (mapped?.field !== undefined) {
            setErrors({ [mapped.field]: mapped.message } as Partial<Record<keyof T, string>>);
            setTouched((current) => ({ ...current, [mapped.field as keyof T]: true }));
            // KF-23(docs/improvement.md): 계약 주석("첫 오류 필드로 포커스를
            // 옮긴다")이 로컬 검증에만 지켜지고 서버 매핑 오류는 빠져 있었다 —
            // 위 로컬 검증 분기와 같은 formRef 룩업으로 맞춘다.
            formRef.current
              ?.querySelector<HTMLElement>(`[name="${String(mapped.field)}"]`)
              ?.focus();
            return;
          }
          setFormError(
            mapped?.message ?? (cause as ApiError).message ?? "요청을 처리하지 못했습니다.",
          );
        })
        .finally(() => setIsSubmitting(false));
    },
    [isSubmitting, schema, values, onSubmit, mapServerError],
  );

  const reset = useCallback(
    (next?: T) => {
      const target = next ?? baseline;
      setBaseline(target);
      setValues(target);
      setErrors({});
      setTouched({});
      setFormError(null);
    },
    [baseline],
  );

  const state: FormState<T> = {
    values,
    errors,
    formError,
    touched,
    isDirty,
    isSubmitting,
    submitCount,
  };

  return { state, field, setValue, handleSubmit, reset };
}
