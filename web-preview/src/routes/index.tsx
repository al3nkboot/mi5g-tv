import { createFileRoute } from "@tanstack/react-router";
import { Studio } from "@/components/mi5g/studio";

export const Route = createFileRoute("/")({ component: Home });

function Home() {
  return <Studio />;
}
