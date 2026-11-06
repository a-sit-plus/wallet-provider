// Provider UI behavior; kept out of the pages for the Content-Security-Policy (script-src 'self').

function parseSortableValue(text) {
    const trimmed = text.trim();
    if (!trimmed || trimmed === "-") {
        return { type: "empty", value: "" };
    }

    const normalizedNumber = trimmed.replace(/\s+/g, "").replace(",", ".");
    if (/^[+-]?\d+(\.\d+)?$/.test(normalizedNumber)) {
        return { type: "number", value: Number(normalizedNumber) };
    }

    return { type: "string", value: trimmed.toLocaleLowerCase() };
}

function compareSortableValues(left, right) {
    if (left.type === "empty" && right.type === "empty") {
        return 0;
    }
    if (left.type === "empty") {
        return 1;
    }
    if (right.type === "empty") {
        return -1;
    }
    if (left.type === right.type) {
        if (left.value < right.value) {
            return -1;
        }
        if (left.value > right.value) {
            return 1;
        }
        return 0;
    }
    return String(left.value).localeCompare(String(right.value), undefined, { numeric: true });
}

function sortTableByColumn(table, columnIndex, direction) {
    const tbody = table.tBodies[0];
    if (!tbody) {
        return;
    }

    const rows = Array.from(tbody.rows);
    rows.sort((a, b) => {
        const aCell = a.cells[columnIndex];
        const bCell = b.cells[columnIndex];
        const aValue = parseSortableValue(aCell ? aCell.innerText : "");
        const bValue = parseSortableValue(bCell ? bCell.innerText : "");
        const result = compareSortableValues(aValue, bValue);
        return direction === "asc" ? result : -result;
    });

    rows.forEach(row => tbody.appendChild(row));
}

// Tables with a header row can be sorted by clicking a column header, except headers marked with data-no-sort.
document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll("table").forEach(table => {
        const headerRow = table.tHead && table.tHead.rows[0];
        const body = table.tBodies[0];
        if (!headerRow || !body || body.rows.length < 2) {
            return;
        }

        const headers = Array.from(headerRow.cells);
        headers.forEach((header, index) => {
            if (header.hasAttribute("data-no-sort")) {
                return;
            }
            header.classList.add("sortable-header");
            header.addEventListener("click", () => {
                const currentDirection = header.dataset.sortDirection === "asc" ? "desc" : "asc";
                headers.forEach(cell => {
                    cell.dataset.sortDirection = "";
                    cell.classList.remove("sort-asc", "sort-desc");
                });
                header.dataset.sortDirection = currentDirection;
                header.classList.add(currentDirection === "asc" ? "sort-asc" : "sort-desc");
                sortTableByColumn(table, index, currentDirection);
            });
        });
    });
});

// A form with data-confirm is only submitted after the user confirms its message.
document.addEventListener("submit", (event) => {
    const message = event.target.getAttribute("data-confirm");
    if (message && !confirm(message)) {
        event.preventDefault();
    }
});
