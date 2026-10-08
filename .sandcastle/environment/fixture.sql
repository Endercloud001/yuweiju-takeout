-- Synthetic test entities only; no original business records or accounts.
INSERT IGNORE INTO employee (id,name,username,password,phone,sex,id_number,status,create_time,update_time,create_user,update_user)
VALUES (900001,'Sandcastle fixture','sandbox_admin','sandbox-only-login','00000000000','1','fixture',1,NOW(),NOW(),900001,900001);
INSERT IGNORE INTO user (id,openid,name,phone,sex) VALUES (900001,'sandbox-user-a','Fixture A','00000000000','1'),(900002,'sandbox-user-b','Fixture B','00000000001','1');
INSERT IGNORE INTO address_book(id,user_id,consignee,phone,detail,is_default) VALUES(900001,900001,'Fixture A','00000000000','Isolation street',1);
INSERT IGNORE INTO shopping_cart(id,name,user_id,dish_id,number,amount,create_time) VALUES(900001,'Fixture dish',900001,900001,1,18.00,NOW());
INSERT IGNORE INTO category(id,type,name,sort,status,create_time,update_time,create_user,update_user) VALUES(900001,1,'Fixture category',1,1,NOW(),NOW(),900001,900001);
INSERT IGNORE INTO dish(id,name,category_id,price,image,status,create_time,update_time,create_user,update_user) VALUES(900001,'Fixture dish',900001,18.00,'',1,NOW(),NOW(),900001,900001);
