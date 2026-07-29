let dateFormatter: Intl.DateTimeFormat | undefined;
let dateTimeFormatter: Intl.DateTimeFormat | undefined;
let numberFormatter: Intl.NumberFormat | undefined;

function getDateFormatter(): Intl.DateTimeFormat {
  dateFormatter ??= new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "long",
    day: "numeric",
    weekday: "short",
  });
  return dateFormatter;
}

function getDateTimeFormatter(): Intl.DateTimeFormat {
  dateTimeFormatter ??= new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "long",
    day: "numeric",
    weekday: "short",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  });
  return dateTimeFormatter;
}

function getNumberFormatter(): Intl.NumberFormat {
  numberFormatter ??= new Intl.NumberFormat("ko-KR");
  return numberFormatter;
}

export function formatDrawDate(value: string): string {
  return getDateFormatter().format(new Date(`${value}T00:00:00+09:00`));
}

export function formatDateTime(value: string): string {
  return getDateTimeFormatter().format(new Date(value));
}

export function formatCurrency(value: number): string {
  return `${getNumberFormatter().format(value)}원`;
}
