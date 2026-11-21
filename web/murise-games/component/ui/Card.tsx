import type { ComponentProps } from "react";
import { cx } from "./cx";

export function Card({
  className,
  ...props
}: ComponentProps<"section">) {
  return <section {...props} className={cx("ui-card", className)} />;
}

export function CardHeader({
  className,
  ...props
}: ComponentProps<"header">) {
  return <header {...props} className={cx("ui-card-header", className)} />;
}

type CardTitleProps = ComponentProps<"h2"> & {
  as?: "h1" | "h2" | "h3";
};

export function CardTitle({
  as: Tag = "h2",
  className,
  ...props
}: CardTitleProps) {
  return <Tag {...props} className={cx("ui-title", className)} />;
}

export function CardDescription({
  className,
  ...props
}: ComponentProps<"p">) {
  return <p {...props} className={cx("ui-description", className)} />;
}