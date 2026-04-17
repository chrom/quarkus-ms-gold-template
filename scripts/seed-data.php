<?php

/**
 * @deprecated Prefer Flyway migration V2__Seed_demo_catalog.sql — same scale (100 categories,
 * 1050 products), no PHP/cURL, no running app. This script remains only if you must seed via REST.
 *
 * Script for bulk seeding the database via REST API.
 * Creates 100 categories and 1050 products.
 *
 * Usage: php scripts/seed-data.php  (from repo root)
 * Requirements: PHP 8.4+, cURL enabled, Quarkus running on localhost:8080
 */

declare(strict_types=1);

// ── Configuration ───────────────────────────────────────────────────────────────
const BASE_URL = 'http://localhost:8080';
const CATEGORIES_COUNT = 100;
const PRODUCTS_COUNT = 1050;
const BATCH_PROGRESS = 50; // Display progress every N records

// ── Arrays for realistic name generation ─────────────────────────────────────
const CATEGORY_NAMES = [
    'Electronics',
    'Laptops',
    'Smartphones',
    'Tablets',
    'Headphones',
    'TVs',
    'Cameras',
    'Game Consoles',
    'Smartwatches',
    'Speakers',
    'Men\'s Clothing',
    'Women\'s Clothing',
    'Shoes',
    'Accessories',
    'Cameras',
    'Game Consoles',
    'Smartwatches',
    'Speakers',
    'Men\'s Clothing',
    'Women\'s Clothing',
    'Shoes',
    'Accessories',
    'Sportswear',
    'Furniture',
    'Lighting',
    'Kitchen Appliances',
    'Tableware',
    'Textiles',
    'Books',
    'Toys',
    'Stationery',
    'Musical Instruments',
    'Sports and Fitness',
    'Auto Parts',
    'Tools',
    'Garden',
    'Pets',
    'Beauty and Health',
    'Food',
    'Drinks',
    'Frozen Food',
    'Dairy Products',
    'Bakery',
    'Electronics',
    'Laptops',
    'Smartphones',
    'Tablets',
    'Headphones',
    'TVs',
    'Cameras',
    'Game Consoles',
    'Smartwatches',
    'Speakers',
    'Men\'s Clothing',
    'Women\'s Clothing',
    'Shoes',
    'Accessories',
    'Sportswear',
    'Furniture',
    'Lighting',
    'Kitchen Appliances',
    'Tableware',
    'Textiles',
    'Books',
    'Toys',
    'Stationery',
    'Musical Instruments',
    'Sports and Fitness',
    'Auto Parts',
    'Tools',
    'Garden',
    'Pets',
    'Beauty and Health',
    'Food',
    'Drinks',
    'Frozen Food',
    'Dairy Products',
    'Bakery',
    'Meat and Fish',
    'Fruits and Vegetables',
    'Snacks',
    'Coffee and Tea',
    'Alcohol',
    'Office Equipment',
    'Networking Equipment',
    'Software',
    'Games',
    'Cables',
    'Jewelry',
    'Watches',
    'Bags',
    'Backpacks',
    'Suitcases',
    'Baby Products',
    'Educational Materials',
    'Puzzles',
    'Board Games',
    'Bicycles',
    'Tourism',
    'Fishing',
    'Skiing',
    'Fitness Equipment',
    'Swimming Pool',
    'Medicines',
    'Vitamins',
    'Coffee',
    'Tea',
    'Bakery',
    'Fruits',
    'Vegetables',
    'Meat',
    'Dairy',
    'Sweets',
    'Beverages',
    'Snacks',
    'Electronics',
    'Computers',
    'Tablets',
    'Smartphones',
    'Headphones',
    'Monitors',
    'Keyboards',
    'Mice',
    'Cables',
    'Adapters',
    'Clothing',
    'Shoes',
    'Bags',
    'Watches',
    'Jewelry',
    'Cosmetics',
    'Perfume',
    'Shampoos',
    'Cream',
    'Conditioners',
    'Washing Machines',
    'Refrigerators',
    'Microwaves',
    'Vacuum Cleaners',
];

const PRODUCT_ADJECTIVES = [
    'Premium',
    'Professional',
    'Compact',
    'Smart',
    'Ergonomic',
    'Wireless',
    'Portable',
    'Fast',
    'Quiet',
    'Reliable',
    'Lightweight',
    'Durable',
    'Stylish',
    'Innovative',
    'Colorful',
];

const PRODUCT_NOUNS = [
    'Pro X',
    'Elite',
    'Max Plus',
    'Ultra',
    'Standard',
    'Basic',
    'Advanced',
    '2024',
    'v2',
    'Series 5',
    'Edition',
    'Deluxe',
    'Lite',
    'Air',
    'Neo',
];

// ── Helper functions ──────────────────────────────────────────────────────────

function apiPost(string $endpoint, array $data): array
{
    $ch = curl_init(BASE_URL . $endpoint);
    curl_setopt_array($ch, [
        CURLOPT_POST => true,
        CURLOPT_POSTFIELDS => json_encode($data),
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_HTTPHEADER => ['Content-Type: application/json', 'Accept: application/json'],
        CURLOPT_TIMEOUT => 10,
    ]);

    $body = curl_exec($ch);
    $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    $error = curl_error($ch);
    curl_close($ch);

    if ($error) {
        throw new RuntimeException("cURL error: $error");
    }

    if ($httpCode >= 400) {
        throw new RuntimeException("API error $httpCode for $endpoint: $body");
    }

    return json_decode($body, true) ?? [];
}

function printProgress(string $label, int $current, int $total): void
{
    $percent = (int) (($current / $total) * 100);
    $bar = str_repeat('█', (int) ($percent / 5)) . str_repeat('░', 20 - (int) ($percent / 5));
    echo "\r  $label [$bar] $current/$total ($percent%)";
}

// ── Step 1: Connection check ───────────────────────────────────────────────
echo "\n";
echo "  ╔══════════════════════════════════════════════════╗\n";
echo "  ║       Quarkus Demo — Bulk Database Seeding       ║\n";
echo "  ╚══════════════════════════════════════════════════╝\n\n";

echo "  🔌 Checking connection to " . BASE_URL . "...\n";
try {
    $check = apiPost('/categories', ['name' => '__health_check__']);
    echo "  ✅ Connection established!\n\n";
} catch (RuntimeException $e) {
    echo "  ❌ Could not connect: " . $e->getMessage() . "\n";
    echo "  Make sure Quarkus is running on " . BASE_URL . "\n\n";
    exit(1);
}

// ── Step 2: Creating categories ───────────────────────────────────────────────
echo "  📦 Creating " . CATEGORIES_COUNT . " categories...\n";

$categoryIds = [];
$availableNames = CATEGORY_NAMES;
shuffle($availableNames);

$startTime = microtime(true);

for ($i = 1; $i <= CATEGORIES_COUNT; $i++) {
    $baseName = $availableNames[($i - 1) % count($availableNames)];
    $name = $i <= count($availableNames) ? $baseName : "$baseName # " . ceil($i / count($availableNames));

    $response = apiPost('/categories', ['name' => $name]);
    $categoryIds[] = $response['id'];

    if ($i % BATCH_PROGRESS === 0 || $i === CATEGORIES_COUNT) {
        printProgress('Categories', $i, CATEGORIES_COUNT);
    }
}

$elapsed = round(microtime(true) - $startTime, 1);
echo "\n  ✅ Created " . count($categoryIds) . " categories in {$elapsed}s\n\n";

// ── Step 3: Creating products ───────────────────────────────────────────────
echo "  🛍️  Creating " . PRODUCTS_COUNT . " products...\n";

$adjectives = PRODUCT_ADJECTIVES;
$nouns = PRODUCT_NOUNS;
$created = 0;
$startTime = microtime(true);

for ($i = 1; $i <= PRODUCTS_COUNT; $i++) {
    $adjective = $adjectives[array_rand($adjectives)];
    $noun = $nouns[array_rand($nouns)];
    $categoryId = $categoryIds[array_rand($categoryIds)];

    $price = round(mt_rand(999, 4999999) / 100, 2);

    apiPost('/products', [
        'name' => "$adjective product $noun #$i",
        'price' => $price,
        'categoryId' => $categoryId,
    ]);

    $created++;

    if ($i % BATCH_PROGRESS === 0 || $i === PRODUCTS_COUNT) {
        printProgress('Products ', $i, PRODUCTS_COUNT);
    }
}

$elapsed = round(microtime(true) - $startTime, 1);
echo "\n  ✅ Created $created products in {$elapsed}s\n\n";

// ── Summary ───────────────────────────────────────────────────────────────────
echo "  ╔══════════════════════════════════════════════════╗\n";
echo "  ║                   DONE! ✅                       ║\n";
printf("  ║  Categories: %-5d                              ║\n", count($categoryIds));
printf("  ║  Products:   %-5d                              ║\n", $created);
echo "  ╚══════════════════════════════════════════════════╝\n";
echo "\n  🌐 Check it: " . BASE_URL . "/products?page=0&size=20\n\n";
