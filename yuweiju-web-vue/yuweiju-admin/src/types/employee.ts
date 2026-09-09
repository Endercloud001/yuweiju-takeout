export interface EmployeeLoginBody {
  username: string
  password: string
}

export interface EmployeeLoginData {
  id: number
  name: string
  token: string
  userName: string
}

export interface EmployeeEditPasswordBody {
  empId: number
  oldPassword: string
  newPassword: string
}

export interface EmployeeItem {
  id: number
  name: string
  username: string
  phone: string
  sex: string
  idNumber: string
  status: number
  createTime: string
  updateTime: string
}

export interface EmployeePageQuery {
  page: number
  pageSize: number
  name?: string
}

export interface EmployeeSaveBody {
  id?: number
  name: string
  username: string
  password: string
  phone: string
  sex: string
  idNumber: string
}

export interface EmployeeStatusBody {
  id: number
  status: number
}
