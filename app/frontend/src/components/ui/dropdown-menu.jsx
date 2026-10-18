import React from "react";
import PropTypes from "prop-types";
import * as MenuPrimitive from "@radix-ui/react-dropdown-menu";
import { cn } from "../../lib/utils";

export const DropdownMenu = MenuPrimitive.Root;
export const DropdownMenuTrigger = MenuPrimitive.Trigger;
export const DropdownMenuSeparator = (props) => (
  <MenuPrimitive.Separator className="my-1 h-px bg-line" {...props} />
);

export function DropdownMenuContent({ className, sideOffset = 8, ...props }) {
  return (
    <MenuPrimitive.Portal>
      <MenuPrimitive.Content
        align="end"
        sideOffset={sideOffset}
        className={cn(
          "z-50 min-w-56 rounded-card border border-line bg-surface p-1.5 shadow-lg",
          className,
        )}
        {...props}
      />
    </MenuPrimitive.Portal>
  );
}
DropdownMenuContent.propTypes = { className: PropTypes.string, sideOffset: PropTypes.number };

export function DropdownMenuItem({ className, ...props }) {
  return (
    <MenuPrimitive.Item
      className={cn(
        "flex cursor-pointer select-none items-center gap-2.5 rounded-control px-2.5 py-2 text-sm outline-none data-[highlighted]:bg-surface-2 [&_svg]:size-4 [&_svg]:text-ink-2",
        className,
      )}
      {...props}
    />
  );
}
DropdownMenuItem.propTypes = { className: PropTypes.string };

export function DropdownMenuLabel({ className, ...props }) {
  return <MenuPrimitive.Label className={cn("px-2.5 py-2 text-sm", className)} {...props} />;
}
DropdownMenuLabel.propTypes = { className: PropTypes.string };
