import { ballColorClass } from "@/lib/ball-color";

type Props = {
  numbers: number[];
  bonusNumber?: number;
};

export function LottoBalls({ numbers, bonusNumber }: Props) {
  const label = `번호 ${numbers.join(", ")}${
    bonusNumber !== undefined ? ` + 보너스 ${bonusNumber}` : ""
  }`;

  return (
    <div className="balls" data-allow-overflow aria-label={label}>
      {numbers.map((number) => (
        <span key={number} className={`ball ${ballColorClass(number)}`}>
          {number}
        </span>
      ))}
      {bonusNumber !== undefined ? (
        <>
          <span className="ball-separator">+</span>
          <span className="ball bonus-ball">{bonusNumber}</span>
        </>
      ) : null}
    </div>
  );
}
