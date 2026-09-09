$ErrorActionPreference = 'Stop'

$BaseUrl = if ($env:YUWEIJU_BASE_URL) { $env:YUWEIJU_BASE_URL } else { 'http://localhost:8080' }
$AdminUsername = if ($env:YUWEIJU_ADMIN_USERNAME) { $env:YUWEIJU_ADMIN_USERNAME } else { 'admin' }
$AdminPassword = if ($env:YUWEIJU_ADMIN_PASSWORD) { $env:YUWEIJU_ADMIN_PASSWORD } else { '123456' }
$UserCode = if ($env:YUWEIJU_USER_CODE) { $env:YUWEIJU_USER_CODE } else { 'test' }

$Failures = New-Object System.Collections.Generic.List[string]

function Read-ErrorBody($err) {
    try {
        $resp = $err.Exception.Response
        if ($null -eq $resp) { return $err.Exception.Message }
        $stream = $resp.GetResponseStream()
        if ($null -eq $stream) { return $err.Exception.Message }
        $reader = New-Object System.IO.StreamReader($stream)
        return $reader.ReadToEnd()
    } catch {
        return $err.Exception.Message
    }
}

function Invoke-JsonApiUtf8Bytes($name, $method, $path, $headers, $bodyObj) {
    $url = if ($path -match '^https?://') { $path } else { "$BaseUrl$path" }
    try {
        if ($null -eq $bodyObj) {
            $res = Invoke-RestMethod -Method $method -Uri $url -Headers $headers
        } else {
            $json = $bodyObj | ConvertTo-Json -Depth 20
            $bytes = [Text.Encoding]::UTF8.GetBytes($json)
            $res = Invoke-RestMethod -Method $method -Uri $url -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $bytes
        }
        if ($res.code -ne $null -and $res.code -ne 1) {
            $Failures.Add("$name -> code=$($res.code) msg=$($res.msg)")
        }
        return $res
    } catch {
        $Failures.Add("$name -> HTTP error: $($_.Exception.Message) body=$(Read-ErrorBody($_))")
        return $null
    }
}

function Invoke-DownloadApi($name, $path, $headers) {
    $url = "$BaseUrl$path"
    try {
        $resp = Invoke-WebRequest -Method GET -Uri $url -Headers $headers -UseBasicParsing
        if ($resp.StatusCode -lt 200 -or $resp.StatusCode -ge 300) {
            $Failures.Add("$name -> status=$($resp.StatusCode)")
        }
    } catch {
        $Failures.Add("$name -> HTTP error: $($_.Exception.Message) body=$(Read-ErrorBody($_))")
    }
}

function New-RandSuffix() {
    return [guid]::NewGuid().ToString('N').Substring(0, 8)
}

function Utf8FromB64($b64) {
    return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($b64))
}

$CnConsignee = Utf8FromB64 '6IGU6LCD55So5oi3'
$CnProvince = Utf8FromB64 '5rGf6IuP55yB'
$CnCity = Utf8FromB64 '5peg6ZSh5biC'
$CnDistrict = Utf8FromB64 '5ruo5rmW5Yy6'
$CnDetail = Utf8FromB64 '5rGf5Y2X5aSn5a2m'
$CnLabel = Utf8FromB64 '5a2m5qCh'

Write-Host "== Auth ==" -ForegroundColor Cyan
$adminLogin = Invoke-JsonApiUtf8Bytes 'admin.login' 'POST' '/admin/employee/login' @{} @{ username = $AdminUsername; password = $AdminPassword }
if ($null -eq $adminLogin -or $adminLogin.code -ne 1) { throw 'admin login failed' }
$adminHeaders = @{ token = $adminLogin.data.token }

$userLogin = Invoke-JsonApiUtf8Bytes 'user.login' 'POST' '/user/user/login' @{} @{ code = $UserCode }
if ($null -eq $userLogin -or $userLogin.code -ne 1) { throw 'user login failed' }
$userHeaders = @{ authentication = $userLogin.data.token }

$suffix = New-RandSuffix

Write-Host "== Admin: category/dish/setmeal CRUD ==" -ForegroundColor Cyan
$dishCategoryName = "lt_dish_$suffix"
$setmealCategoryName = "lt_setmeal_$suffix"
$dishName = "lt_dish_item_$suffix"
$setmealName = "lt_setmeal_item_$suffix"

Invoke-JsonApiUtf8Bytes 'admin.category.create.dish' 'POST' '/admin/category' $adminHeaders @{ name = $dishCategoryName; type = 1; sort = 999 } | Out-Null
$dishCategory = (Invoke-JsonApiUtf8Bytes 'admin.category.list.dish' 'GET' '/admin/category/list?type=1' $adminHeaders $null).data | Where-Object { $_.name -eq $dishCategoryName } | Select-Object -First 1
$dishCategoryId = $dishCategory.id

Invoke-JsonApiUtf8Bytes 'admin.category.create.setmeal' 'POST' '/admin/category' $adminHeaders @{ name = $setmealCategoryName; type = 2; sort = 999 } | Out-Null
$setmealCategory = (Invoke-JsonApiUtf8Bytes 'admin.category.list.setmeal' 'GET' '/admin/category/list?type=2' $adminHeaders $null).data | Where-Object { $_.name -eq $setmealCategoryName } | Select-Object -First 1
$setmealCategoryId = $setmealCategory.id

$dishBody = @{
    name        = $dishName
    categoryId  = $dishCategoryId
    price       = 1.00
    image       = 'http://example.com/dish.png'
    description = 'test'
    status      = 1
    flavors     = @(@{ name = 'spicy'; value = '0,1' })
}
Invoke-JsonApiUtf8Bytes 'admin.dish.create' 'POST' '/admin/dish' $adminHeaders $dishBody | Out-Null
$dish = (Invoke-JsonApiUtf8Bytes 'admin.dish.page' 'GET' "/admin/dish/page?page=1&pageSize=20&name=$dishName" $adminHeaders $null).data.records | Select-Object -First 1
$dishId = $dish.id
Invoke-JsonApiUtf8Bytes 'admin.dish.getById' 'GET' "/admin/dish/$dishId" $adminHeaders $null | Out-Null
Invoke-JsonApiUtf8Bytes 'admin.dish.status.off' 'POST' "/admin/dish/status/0?id=$dishId" $adminHeaders $null | Out-Null
Invoke-JsonApiUtf8Bytes 'admin.dish.status.on' 'POST' "/admin/dish/status/1?id=$dishId" $adminHeaders $null | Out-Null

$setmealBody = @{
    name          = $setmealName
    categoryId    = $setmealCategoryId
    price         = 2.00
    image         = 'http://example.com/setmeal.png'
    description   = 'test'
    status        = 0
    setmealDishes = @(@{ dishId = $dishId; copies = 1 })
}
Invoke-JsonApiUtf8Bytes 'admin.setmeal.create' 'POST' '/admin/setmeal' $adminHeaders $setmealBody | Out-Null
$setmeal = (Invoke-JsonApiUtf8Bytes 'admin.setmeal.page' 'GET' "/admin/setmeal/page?page=1&pageSize=20&name=$setmealName" $adminHeaders $null).data.records | Select-Object -First 1
$setmealId = $setmeal.id
Invoke-JsonApiUtf8Bytes 'admin.setmeal.getById' 'GET' "/admin/setmeal/$setmealId" $adminHeaders $null | Out-Null
Invoke-JsonApiUtf8Bytes 'admin.setmeal.status.on' 'POST' "/admin/setmeal/status/1?id=$setmealId" $adminHeaders $null | Out-Null
Invoke-JsonApiUtf8Bytes 'admin.setmeal.status.off' 'POST' "/admin/setmeal/status/0?id=$setmealId" $adminHeaders $null | Out-Null

Write-Host "== User: browse + address + order ==" -ForegroundColor Cyan
Invoke-JsonApiUtf8Bytes 'user.shop.status' 'GET' '/user/shop/status' @{} $null | Out-Null
$userCategories = (Invoke-JsonApiUtf8Bytes 'user.category.list' 'GET' '/user/category/list?type=1' @{} $null).data
$userDishCategoryId = ($userCategories | Where-Object { $_.name -eq $dishCategoryName } | Select-Object -First 1).id
$userDishes = (Invoke-JsonApiUtf8Bytes 'user.dish.list' 'GET' "/user/dish/list?categoryId=$userDishCategoryId" @{} $null).data
$userDishId = ($userDishes | Where-Object { $_.name -eq $dishName } | Select-Object -First 1).id

$addrBody = @{ consignee = $CnConsignee; sex = '1'; phone = '13800000000'; provinceName = $CnProvince; cityName = $CnCity; districtName = $CnDistrict; detail = $CnDetail; label = $CnLabel; isDefault = 1 }
Invoke-JsonApiUtf8Bytes 'user.address.create' 'POST' '/user/addressBook' $userHeaders $addrBody | Out-Null
$addrList = Invoke-JsonApiUtf8Bytes 'user.address.list' 'GET' '/user/addressBook/list' $userHeaders $null
$addressId = $addrList.data[0].id
Invoke-JsonApiUtf8Bytes 'user.order.estimatedDeliveryTime' 'GET' "/user/order/estimatedDeliveryTime?addressBookId=$addressId" $userHeaders $null | Out-Null

Invoke-JsonApiUtf8Bytes 'user.cart.clean' 'DELETE' '/user/shoppingCart/clean' $userHeaders $null | Out-Null
Invoke-JsonApiUtf8Bytes 'user.cart.add' 'POST' '/user/shoppingCart/add' $userHeaders @{ dishId = $userDishId } | Out-Null
Invoke-JsonApiUtf8Bytes 'user.cart.list' 'GET' '/user/shoppingCart/list' $userHeaders $null | Out-Null

$submit = Invoke-JsonApiUtf8Bytes 'user.order.submit' 'POST' '/user/order/submit' $userHeaders @{ addressBookId = $addressId; payMethod = 1; remark = ''; deliveryStatus = 1; packAmount = 3; tablewareNumber = 1; tablewareStatus = 1 }
$orderId = $submit.data.id
$orderNumber = $submit.data.orderNumber

Invoke-JsonApiUtf8Bytes 'user.order.detail' 'GET' "/user/order/orderDetail/$orderId" $userHeaders $null | Out-Null
Invoke-JsonApiUtf8Bytes 'user.order.history' 'GET' '/user/order/historyOrders?page=1&pageSize=5' $userHeaders $null | Out-Null
Invoke-JsonApiUtf8Bytes 'user.order.reminder' 'GET' "/user/order/reminder/$orderId" $userHeaders $null | Out-Null
Invoke-JsonApiUtf8Bytes 'user.order.repetition' 'POST' "/user/order/repetition/$orderId" $userHeaders $null | Out-Null
Invoke-JsonApiUtf8Bytes 'user.order.payment' 'PUT' '/user/order/payment' $userHeaders @{ orderNumber = $orderNumber; payMethod = 1 } | Out-Null

Write-Host "== Admin: workspace/order/report ==" -ForegroundColor Cyan
Invoke-JsonApiUtf8Bytes 'admin.workspace.businessData' 'GET' '/admin/workspace/businessData' $adminHeaders $null | Out-Null
Invoke-JsonApiUtf8Bytes 'admin.workspace.overviewOrders' 'GET' '/admin/workspace/overviewOrders' $adminHeaders $null | Out-Null
Invoke-JsonApiUtf8Bytes 'admin.workspace.overviewDishes' 'GET' '/admin/workspace/overviewDishes' $adminHeaders $null | Out-Null
Invoke-JsonApiUtf8Bytes 'admin.workspace.overviewSetmeals' 'GET' '/admin/workspace/overviewSetmeals' $adminHeaders $null | Out-Null

Invoke-JsonApiUtf8Bytes 'admin.order.statistics' 'GET' '/admin/order/statistics' $adminHeaders $null | Out-Null
Invoke-JsonApiUtf8Bytes 'admin.order.details' 'GET' "/admin/order/details/$orderId" $adminHeaders $null | Out-Null
Invoke-JsonApiUtf8Bytes 'admin.order.confirm' 'PUT' '/admin/order/confirm' $adminHeaders @{ id = $orderId } | Out-Null
Invoke-JsonApiUtf8Bytes 'admin.order.delivery' 'PUT' "/admin/order/delivery/$orderId" $adminHeaders $null | Out-Null
Invoke-JsonApiUtf8Bytes 'admin.order.complete' 'PUT' "/admin/order/complete/$orderId" $adminHeaders $null | Out-Null
Invoke-JsonApiUtf8Bytes 'admin.order.conditionSearch' 'GET' "/admin/order/conditionSearch?page=1&pageSize=5&number=$orderNumber" $adminHeaders $null | Out-Null

$today = (Get-Date).ToString('yyyy-MM-dd')
Invoke-JsonApiUtf8Bytes 'admin.report.turnover' 'GET' "/admin/report/turnoverStatistics?begin=$today&end=$today" $adminHeaders $null | Out-Null
Invoke-JsonApiUtf8Bytes 'admin.report.userStats' 'GET' "/admin/report/userStatistics?begin=$today&end=$today" $adminHeaders $null | Out-Null
Invoke-JsonApiUtf8Bytes 'admin.report.ordersStats' 'GET' "/admin/report/ordersStatistics?begin=$today&end=$today" $adminHeaders $null | Out-Null
Invoke-JsonApiUtf8Bytes 'admin.report.top10' 'GET' "/admin/report/top10?begin=$today&end=$today" $adminHeaders $null | Out-Null
Invoke-DownloadApi 'admin.report.export' "/admin/report/export?begin=$today&end=$today" $adminHeaders

Write-Host "== Cleanup ==" -ForegroundColor Cyan
Invoke-JsonApiUtf8Bytes 'admin.setmeal.delete' 'DELETE' "/admin/setmeal?ids=$setmealId" $adminHeaders $null | Out-Null
Invoke-JsonApiUtf8Bytes 'admin.dish.delete' 'DELETE' "/admin/dish?ids=$dishId" $adminHeaders $null | Out-Null
Invoke-JsonApiUtf8Bytes 'admin.category.delete.dish' 'DELETE' "/admin/category?id=$dishCategoryId" $adminHeaders $null | Out-Null
Invoke-JsonApiUtf8Bytes 'admin.category.delete.setmeal' 'DELETE' "/admin/category?id=$setmealCategoryId" $adminHeaders $null | Out-Null

Invoke-JsonApiUtf8Bytes 'user.logout' 'POST' '/user/user/logout' $userHeaders $null | Out-Null
Invoke-JsonApiUtf8Bytes 'admin.logout' 'POST' '/admin/employee/logout' $adminHeaders $null | Out-Null

if ($Failures.Count -gt 0) {
    Write-Host "`nFAILED ($($Failures.Count))" -ForegroundColor Red
    $Failures | ForEach-Object { Write-Host "- $_" -ForegroundColor Red }
    exit 2
}

Write-Host "`nALL OK" -ForegroundColor Green

