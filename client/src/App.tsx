import { useEffect, useState } from "react";
import { DocPage } from "./components/DocPage";
import { currentDocId } from "./lib/identity";

/** Picks the document from the URL hash and remounts the page when it changes. */
export default function App() {
  const [docId, setDocId] = useState(currentDocId);

  useEffect(() => {
    const onHashChange = () => setDocId(currentDocId());
    window.addEventListener("hashchange", onHashChange);
    return () => window.removeEventListener("hashchange", onHashChange);
  }, []);

  return <DocPage key={docId} docId={docId} />;
}
