/**
 * PNG 아이콘. 파일은 public/icons/ui/*.png (Lucide 아이콘을 색별로 PNG로 만든 것, ISC 라이선스)
 * 장식용이라 기본은 alt="" — 의미가 있는 아이콘이면 label 을 넘긴다.
 */
export default function Icon({ name, size = 20, label, className = '' }) {
  return (
    <img
      src={`/icons/ui/${name}.png`}
      width={size}
      height={size}
      alt={label ?? ''}
      aria-hidden={label ? undefined : true}
      className={`ico ${className}`}
      draggable={false}
    />
  )
}
