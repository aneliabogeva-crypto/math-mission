# Topic 5 "Разлагане на множители", excellent level: practice tests for C12 (formulas), C14 (grouping)
# and C15 (combined methods), plus class-test variants aimed at the grade "Отличен (6)".
# Built after every other generator with its own seed, so published items stay unchanged.
import random

import factor_templates as FT
import gen_tests as g
import topic_factor as TFa
import topic_formulas as TF

POOLS = {
    "FM": ([FT.fm_square2_mc, FT.fm_ds_mono_mc, FT.fm_cubes_mc, TF.ds_factor_ok],
           [FT.fm_cubes_expr, FT.fm_ds_binom_expr, TFa.f_ds_number],
           [FT.fm_steps, FT.fm_structured]),
    "GR": ([FT.gr_four_mc, FT.gr_cubic_mc, FT.gr_split_mc],
           [FT.gr_four_expr, FT.gr_cubic_expr, FT.gr_split_expr, FT.gr_ds_group, FT.gr_number],
           [FT.gr_steps, FT.gr_structured]),
    "CB": ([FT.cb_cf_ds_mc, FT.cb_fourth_mc, FT.cb_group_ds_mc, FT.cb_sq_minus_mc, FT.cb_divis_mc],
           [FT.cb_cf_sq_expr, FT.cb_group_ds_expr, FT.cb_cubes_expr, FT.cb_binoms_expr, FT.cb_sq_minus_expr, FT.cb_number],
           [FT.cb_steps, FT.cb_structured, FT.cb_equation]),
}
TOPIC = "Разлагане на множители"
EXAM = "Подготовка за контролно"
TESTS = [
    ("FAC-4A", ["FM"], f"{TOPIC} · Формули — тренировка за отличен 1", "ALLOWED"),
    ("FAC-4B", ["FM"], f"{TOPIC} · Формули — тренировка за отличен 2", "ALLOWED"),
    ("FAC-5A", ["GR"], f"{TOPIC} · Групиране — тренировка 1", "ALLOWED"),
    ("FAC-5B", ["GR"], f"{TOPIC} · Групиране — тренировка 2", "ALLOWED"),
    ("FAC-6A", ["CB"], f"{TOPIC} · Комбинирано разлагане — тренировка 1", "ALLOWED"),
    ("FAC-6B", ["CB"], f"{TOPIC} · Комбинирано разлагане — тренировка 2", "ALLOWED"),
    ("FAC-X1", ["CB", "GR", "FM"], f"{EXAM} · Разлагане за отличен — вариант 1", "NONE"),
    ("FAC-X2", ["CB", "GR", "FM"], f"{EXAM} · Разлагане за отличен — вариант 2", "NONE"),
    ("FAC-X3", ["CB", "GR", "FM"], f"{EXAM} · Разлагане за отличен — вариант 3", "NONE"),
    ("FAC-X4", ["CB", "GR", "FM"], f"{EXAM} · Разлагане за отличен — вариант 4", "NONE"),
]


def build(existing_prompts):
    rng = random.Random(20261106)
    seen = set(existing_prompts)
    questions, tests = [], []
    for key, topics, title, hints in TESTS:
        items, texts = [], set()
        for slot, n in enumerate((12, 5, 3)):
            pools = [(t, POOLS[t][slot]) for t in topics]
            for i in range(n):
                t, pool = pools[i % len(pools)]
                made = None
                for attempt in range(200):
                    fn = pool[(i // len(pools) + attempt) % len(pool)]
                    k = f"{key}-{len(items) + 1:02d}"
                    g.SIDE.pop(k, None)
                    try:
                        qd = fn(rng, k)
                    except (ValueError, ZeroDivisionError, KeyError):
                        continue
                    text = qd["draft"]["prompt"]["text"]
                    if text.lower() in texts or TF.sig(qd) in seen or (not qd["draft"]["prompt"].get("options") and text in seen):
                        continue
                    made = qd
                    break
                if not made:
                    raise RuntimeError(f"cannot build {key} item {len(items) + 1}")
                texts.add(made["draft"]["prompt"]["text"].lower())
                seen.add(TF.sig(made))
                if not made["draft"]["prompt"].get("options"):
                    seen.add(made["draft"]["prompt"]["text"])
                items.append(made)
        questions += items
        tests.append({"testKey": key, "title": title, "kind": "THEMATIC", "path": "C", "blueprint": "Тематичен тест (20)",
                      "timeLimitMin": 40, "hintPolicy": hints, "reviewMoment": "AFTER_SUBMIT",
                      "questionKeys": [x["key"] for x in items]})
    return questions, tests
