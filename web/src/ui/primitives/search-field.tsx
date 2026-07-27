import type { FormEvent } from "react";
import type { SearchFieldContract } from "./contracts";
import { IconButton } from "./icon-button";
import fieldStyles from "./field.module.css";
import styles from "./search-field.module.css";

export function SearchField({ id, minLength, maxLength, value, onChange, onSubmit, placeholder }: SearchFieldContract) {
  const submitIfValid = () => {
    if (value.length >= minLength) {
      onSubmit(value);
    }
  };

  const handleFormSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    submitIfValid();
  };

  return (
    <form role="search" className={styles.form} onSubmit={handleFormSubmit}>
      <input
        id={id}
        type="search"
        aria-label="검색어 입력"
        className={`${fieldStyles.control} ${styles.input}`}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        minLength={minLength}
        maxLength={maxLength}
      />
      {/* IconButton은 type="button" 고정이라 폼 제출을 트리거하지 않으므로 동일 로직을 직접 호출한다. */}
      <IconButton aria-label="검색" variant="secondary" icon={<span>🔍</span>} onClick={submitIfValid} />
    </form>
  );
}
