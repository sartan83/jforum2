"""Headless-Chrome screenshots of the main JForum pages (demo profile).

    pip install playwright
    python3 docs/verification/screenshots.py [base-url] [chrome-executable]
"""
import pathlib
import sys

from playwright.sync_api import sync_playwright

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080"
CHROME = sys.argv[2] if len(sys.argv) > 2 else None
OUT = pathlib.Path(__file__).parent / "screenshots"
OUT.mkdir(exist_ok=True)


def shot(page, name, path):
    page.goto(BASE + path)
    page.wait_for_load_state("networkidle")
    page.screenshot(path=str(OUT / f"{name}.png"), full_page=True)
    print(f"{name}.png  {path}")


def login(page, username, password):
    page.goto(BASE + "/user/login.page")
    page.fill("input[name=username]", username)
    page.fill("input[name=password]", password)
    page.click("input[name=login]")
    page.wait_for_load_state("networkidle")


with sync_playwright() as p:
    browser = p.chromium.launch(headless=True, executable_path=CHROME)

    anon = browser.new_page(viewport={"width": 1280, "height": 900})
    shot(anon, "01-forum-list", "/forums/list.page")
    shot(anon, "02-forum-show", "/forums/show/2.page")
    shot(anon, "03-topic", "/posts/list/2.page")
    shot(anon, "04-login", "/user/login.page")
    shot(anon, "05-search", "/jforum.page?module=search&action=search&search_keywords=Atlas&match_type=all&sort_by=time&sort_dir=DESC")
    shot(anon, "06-member-list", "/user/list.page")

    user = browser.new_context(viewport={"width": 1280, "height": 900}).new_page()
    login(user, "mario", "demo")
    shot(user, "07-logged-in-forum-list", "/forums/list.page")
    shot(user, "08-new-topic-form", "/posts/insert/4.page")
    shot(user, "09-pm-sentbox", "/pm/sentbox.page")

    admin = browser.new_context(viewport={"width": 1280, "height": 900}).new_page()
    login(admin, "admin", "admin")
    shot(admin, "10-admin-main", "/jforum.page?module=admBase&action=main")
    shot(admin, "11-admin-menu", "/jforum.page?module=admBase&action=menu")
    shot(admin, "12-admin-forums", "/adminForums/list.page")
    shot(admin, "13-admin-users", "/adminUsers/list.page")
    shot(admin, "14-admin-group-permissions", "/adminGroups/permissions/1.page")

    browser.close()
